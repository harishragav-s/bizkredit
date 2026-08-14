import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import NotificationBell from './NotificationBell';
import LogoMark from './LogoMark';


export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav className="bk-navbar">
      <div className="bk-navbar-brand">
        <LogoMark size={32} />
        <div>
          <span className="bk-navbar-brand-text">BIZKREDIT</span>
          <span className="bk-navbar-brand-sub">BANKING SOLUTIONS</span>
        </div>
      </div>

      {user && (
        <div className="bk-navbar-user">
          <NotificationBell />
          <span className="bk-navbar-username d-none d-md-inline">{user.name}</span>
          <span className="bk-role-badge">{user.role.replaceAll('_', ' ')}</span>
          <button className="bk-btn-ghost" onClick={handleLogout}>
            <i className="bi bi-box-arrow-right"></i>
            <span className="d-none d-md-inline">Log out</span>
          </button>
        </div>
      )}
    </nav>
  );
}
