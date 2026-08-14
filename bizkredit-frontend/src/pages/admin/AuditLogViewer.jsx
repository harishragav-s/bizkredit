import React, { useEffect, useState, useMemo } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import authService from '../../services/authService';

export default function AuditLogViewer() {
  const [users, setUsers] = useState([]);
  const [logs, setLogs] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(0);
  const [userId, setUserId] = useState('');
  const [error, setError] = useState('');


 const userMap = useMemo(() => {
   const map = {};

   for (const user of users) {
     map[user.userId] = user.name || user.email;
   }

   return map;
 }, [users]);


  useEffect(() => {
    authService.getAllUsers()
      .then((res) => {
        const fetchedUsers = res.data.data || [];
        setUsers(fetchedUsers);
      })
      .catch(() => setUsers([]));
  }, []);


  useEffect(() => {
    const params = { page, size: 20 };
    if (userId) params.userId = userId;

    authService.searchAuditLogs(params)
      .then((res) => {
        const all = res.data.data.content || [];

        setLogs(all.filter((logs) => logs.action === 'LOGIN' || logs.action === 'LOGOUT'));
        setTotalPages(res.data.data.totalPages || 0);
        setError('');
      })
      .catch((err) => setError(err.response?.data?.message || 'Could not load login trail.'));
  }, [page, userId]);

  // reset pagination when filtering by a new user
  const handleUserChange = (newId) => {
    setUserId(newId);
    setPage(0);
  };

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="ADMIN" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Admin Console"
            title="Audit Trail"
            subtitle="Login and Logout activity, by user"
          />

          {error && <div className="alert alert-danger">{error}</div>}

          <div className="d-flex gap-2 align-items-end mb-3">
            <div style={{ minWidth: '280px' }}>
              <label className="bk-label">User</label>
              <select
                className="form-select bk-input"
                value={userId}
                onChange={(e) => handleUserChange(e.target.value)}
              >
                <option value="">All users</option>
                {users.map((u) => (
                  <option key={u.userId} value={u.userId}>
                    {u.name || u.email}
                  </option>
                ))}
              </select>
            </div>
            {userId && (
              <button type="button" className="btn btn-bk-outline" onClick={() => handleUserChange('')}>
                Clear
              </button>
            )}
          </div>

          <table className="table bk-table">
            <thead>
              <tr><th>Timestamp</th><th>User</th><th>Event</th></tr>
            </thead>
            <tbody>
              {logs.map((l) => (
                <tr key={l.auditId}>
                  <td className="small text-muted">{l.timestamp}</td>
                  <td>{userMap[l.userId] || 'Unknown user'}</td>
                  <td>
                    {l.action === 'LOGIN' ? (
                      <span className="badge text-bg-success">Login</span>
                    ) : (
                      <span className="badge text-bg-secondary">Logout</span>
                    )}
                  </td>
                </tr>
              ))}
              {logs.length === 0 && (
                <tr><td colSpan={3} className="text-center text-muted py-5 small">No login/logout activity matches this filter.</td></tr>
              )}
            </tbody>
          </table>

          {totalPages > 1 && (
            <div className="d-flex gap-2">
              <button
                className="btn btn-sm btn-bk-outline"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                Previous
              </button>
              <span className="small text-muted align-self-center">Page {page + 1} of {totalPages}</span>
              <button
                className="btn btn-sm btn-bk-outline"
                disabled={page >= totalPages - 1}
                onClick={() => setPage(page + 1)}
              >
                Next
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}