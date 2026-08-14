import api from './api';


const authService = {
  register: (data) => api.post('/api/auth/register', data),
  registerStaff: (data) => api.post('/api/auth/register-staff', data),
  login: (data) => api.post('/api/auth/login', data),
  logout: (userId) => api.post(`/api/auth/logout?userId=${userId}`),

  getAllUsers: () => api.get('/api/users'),
  updateUserStatus: (id, value) => api.patch(`/api/users/${id}/status?value=${value}`),

  searchAuditLogs: (params = {}) => api.get('/api/audit-logs/search', { params }),
};

export default authService;
