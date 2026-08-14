import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import authService from '../../services/authService';

const STATUSES = ['Active', 'Locked', 'Inactive'];

export default function UserManagement() {
  const [users, setUsers] = useState([]);
  const [error, setError] = useState('');

  const loadUsers = () => {
    authService
      .getAllUsers()
      .then((response) => setUsers(response.data.data))
      .catch((apiError) => setError(apiError.response?.data?.message || 'Could not load users.'));
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const handleStatusChange = async (id, value) => {
    setError('');
    try {
      await authService.updateUserStatus(id, value);
      loadUsers();
    } catch (apiError) {
      setError(apiError.response?.data?.message || 'Could not update status.');
    }
  };

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="ADMIN" />
        <div className="bk-content">
          <PageHeader eyebrow="Admin Console" title="User Management" />

          {error && <div className="alert alert-danger">{error}</div>}

          <table className="table bk-table">
            <thead>
              <tr><th>Name</th><th>Email</th><th>Role</th><th>Branch</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.userId}>
                  <td>{user.name}</td>
                  <td>{user.email}</td>
                  <td>{user.role?.replaceAll('_', ' ')}</td>
                  <td>{user.branchId || '—'}</td>
                  <td>
                    <span className={`badge text-bg-${user.status === 'Active' ? 'success' : 'neutral'}`}>
                      {user.status}
                    </span>
                  </td>
                  <td>
                    <select
                      className="form-select bk-input"
                      style={{ padding: '0.3rem 0.5rem' }}
                      value={user.status}
                      onChange={(event) => handleStatusChange(user.userId, event.target.value)}
                    >
                      {STATUSES.map((status) => <option key={status} value={status}>{status}</option>)}
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}