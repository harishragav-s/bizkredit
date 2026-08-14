import api from './api';

// Covenant & Portfolio Monitoring, NPA, Notifications - routed by the
// gateway to monitoring-service
const monitoringService = {
  // Covenants
  createCovenant: (facilityId, data) => api.post(`/api/facilities/${facilityId}/covenants`, data),
  getCovenants: (facilityId) => api.get(`/api/facilities/${facilityId}/covenants`),
  waiveCovenant: (facilityId, id) => api.patch(`/api/facilities/${facilityId}/covenants/${id}/waive`),

  // Covenant Tracking (BP2-23)
  recordCovenantTracking: (covenantId, data) => api.post(`/api/covenants/${covenantId}/tracking`, data),
  getCovenantTrackingHistory: (covenantId) => api.get(`/api/covenants/${covenantId}/tracking`),

  // Watchlist (BP2-39)
  getWatchlist: () => api.get('/api/covenants/watchlist'),
  checkOverdueTracking: () => api.post('/api/covenants/check-due'),

  // Covenant Templates (BP2-43/52)
  getCovenantTemplates: (params = {}) => api.get('/api/covenant-templates', { params }),

  // Early Warning Signals
  getEWS: (facilityId) => api.get(`/api/facilities/${facilityId}/ews`),

  // Notifications
  getNotifications: (userId, params = {}) =>
    api.get('/api/notifications', { params: { userId, ...params } }),
  markNotificationRead: (id) => api.patch(`/api/notifications/${id}/read`),
  dismissNotification: (id) => api.patch(`/api/notifications/${id}/dismiss`),

  // Portfolio
  getPortfolioSummary: () => api.get('/api/portfolio/summary'),
  getAssetQuality: () => api.get('/api/portfolio/asset-quality'),
  getSectorExposure: () => api.get('/api/portfolio/sector-exposure'),
  getCovenantComplianceSummary: () => api.get('/api/portfolio/covenant-compliance'),
  getEwsSignalSummary: () => api.get('/api/portfolio/ews-signals'),
  getRenewalPipeline: () => api.get('/api/portfolio/renewal-pipeline'),
};

export default monitoringService;
