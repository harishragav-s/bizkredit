import React, { useEffect, useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import monitoringService from '../services/monitoringService';

export default function NotificationBell() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [notifications, setNotifications] = useState([]);
  const [open, setOpen] = useState(false);
  const dropdownRef = useRef(null);

  const load = () => {
    if (!user?.userId) return;
    monitoringService
      .getNotifications(user.userId)
      .then((res) => setNotifications(res.data.data || []))
      .catch(() => {});
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 30000);
    return () => clearInterval(interval);

  }, [user?.userId]);

  useEffect(() => {
    const handler = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const unreadCount = notifications.filter((n) => n.status === 'UNREAD').length;

  const handleMarkRead = async (id) => {
    try {
      await monitoringService.markNotificationRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.notificationId === id ? { ...n, status: 'READ' } : n))
      );
    } catch {}
  };

  const handleMarkAllRead = async () => {
    const unread = notifications.filter((n) => n.status === 'UNREAD');
    await Promise.all(unread.map((n) => monitoringService.markNotificationRead(n.notificationId).catch(() => {})));
    setNotifications((prev) => prev.map((n) => ({ ...n, status: 'READ' })));
  };

  const handleDismiss = async (e, id) => {
    e.stopPropagation();
    try {
      await monitoringService.dismissNotification(id);
      setNotifications((prev) => prev.filter((n) => n.notificationId !== id));
    } catch {}
  };

  if (!user?.userId) return null;

  return (
    <div className="position-relative" ref={dropdownRef}>
      <button
        className="bk-btn-ghost position-relative"
        onClick={() => setOpen((o) => !o)}
        aria-label="Notifications"
      >
        <i className="bi bi-bell"></i>
        {unreadCount > 0 && (
          <span className="bk-notif-badge">{unreadCount}</span>
        )}
      </button>

      {open && (
        <div className="bk-notif-dropdown">
          <div className="bk-notif-header">
            <span className="bk-notif-title">Notifications</span>
            {unreadCount > 0 && (
              <button className="btn btn-link p-0 bk-login-footer-link"  onClick={handleMarkAllRead}>
                Mark all read
              </button>
            )}
          </div>

          {notifications.length === 0 ? (
            <div className="text-center text-muted py-4">
              <i className="bi bi-bell-slash fs-1 text-secondary d-block mb-2"></i>
              <p className="small mb-0">No notifications yet.</p>
            </div>
          ) : (
            notifications.slice().reverse().slice(0, 10).map((n) => (
              <div
                key={n.notificationId}
                className={`bk-notif-item ${n.status === 'UNREAD' ? 'unread' : ''}`}
                onClick={() => n.status === 'UNREAD' && handleMarkRead(n.notificationId)}
              >
                <div className="d-flex justify-content-between align-items-start">
                  <div className="bk-notif-msg">{n.message}</div>
                  <button
                    className="btn btn-link p-0 text-muted"
                    style={{ fontSize: '0.75rem', lineHeight: 1 }}
                    onClick={(e) => handleDismiss(e, n.notificationId)}
                    aria-label="Dismiss notification"
                    title="Dismiss"
                  >
                    <i className="bi bi-x-lg"></i>
                  </button>
                </div>
                <div className="d-flex justify-content-between bk-notif-meta mt-1">
                  <span>{n.category}</span>
                  <span>{n.createdDate}</span>
                </div>
              </div>
            ))
          )}
          <div className="text-center border-top">
            <button
              className="btn btn-link p-2 bk-login-footer-link"
              onClick={() => { setOpen(false); navigate('/notifications'); }}
            >
              View All Notifications
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
