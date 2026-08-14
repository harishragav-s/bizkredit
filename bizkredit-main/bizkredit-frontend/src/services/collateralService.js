import api from './api';

// Collateral, Facility, Disbursement & Repayment - routed by the
// gateway to collateral-service
const collateralService = {
  // Collateral
  registerCollateral: (appId, data) => api.post(`/api/loan-applications/${appId}/collaterals`, data),
  evaluateCollateral: (appId, id, confirmedMarketValue) =>
    api.post(`/api/loan-applications/${appId}/collaterals/${id}/evaluate?confirmedMarketValue=${confirmedMarketValue}`),
  getCollateralsByApplication: (appId) => api.get(`/api/loan-applications/${appId}/collaterals`),
  revalueCollateral: (appId, id, newValue, revaluedById) =>
    api.post(`/api/loan-applications/${appId}/collaterals/${id}/revalue?newValue=${newValue}&revaluedById=${revaluedById}`),

  // Facility
  createFacility: (applicationId, businessId, data) =>
    api.post(`/api/facilities?applicationId=${applicationId}&businessId=${businessId}`, data),
  getFacility: (id) => api.get(`/api/facilities/${id}`),
  closeFacility: (id) => api.patch(`/api/facilities/${id}/close`),
  deleteFacility: (id) => api.delete(`/api/facilities/${id}`),
  getFacilitiesByBusiness: (businessId) => api.get('/api/facilities', { params: { businessId } }),
  getAllFacilities: (status) => api.get('/api/facilities', { params: status ? { status } : {} }),

  // Drawdowns
  requestDrawdown: (facilityId, data) => api.post(`/api/facilities/${facilityId}/drawdowns`, data),
  getDrawdowns: (facilityId) => api.get(`/api/facilities/${facilityId}/drawdowns`),
  disburseDrawdown: (facilityId, id) => api.patch(`/api/facilities/${facilityId}/drawdowns/${id}/disburse`),

  // Maker-Checker
  submitForApproval: (data) => api.post('/api/maker-checker', data),

  recordRepayment: (drawdownId, data) =>
    api.post(`/api/repayments?drawdownId=${drawdownId}`, data),
  getRepaymentsByFacility: (facilityId) => api.get('/api/repayments', { params: { facilityId } }),
  verifyRepayment: (id, verifiedById) => api.post(`/api/repayments/${id}/verify?verifiedById=${verifiedById}`),
};

export default collateralService;
