import api from './api';

// SME Onboarding & Loan Origination - routed by the gateway to sme-loan-service
const smeService = {
  // SME Business
  registerBusiness: (data) => api.post('/api/sme-businesses', data),
  getBusiness: (id) => api.get(`/api/sme-businesses/${id}`),
  getAllBusinesses: (params = {}) => api.get('/api/sme-businesses', { params }),
  getMyBusinesses: (applicantUserId) => api.get('/api/my-businesses', { params: { applicantUserId } }),
  updateKycStatus: (id, status, remarks) =>
    api.patch(`/api/sme-businesses/${id}/kyc-status`, null, { params: { status, remarks } }),

  // Promoters
  addPromoter: (businessId, data) => api.post(`/api/sme-businesses/${businessId}/promoters`, data),
  getPromoters: (businessId) => api.get(`/api/sme-businesses/${businessId}/promoters`),

  // Loan Applications
  createApplication: (businessId, data) =>
    api.post(`/api/loan-applications?businessId=${businessId}`, data),
  submitApplication: (id) => api.patch(`/api/loan-applications/${id}/submit`),
  getApplications: (params = {}) => api.get('/api/loan-applications', { params }),
  assignAnalyst: (id, analystId) => api.patch(`/api/loan-applications/${id}/assign?analystId=${analystId}`),

  // Uploads a KYC document directly to a business (no application).
  uploadBusinessDocumentFile: (businessId, { documentType, financialYear, file }) => {
    const formData = new FormData();
    formData.append('documentType', documentType);
    formData.append('financialYear', financialYear || '');
    formData.append('file', file);
    return api.post(`/api/sme-businesses/${businessId}/documents/upload`, formData, {
      headers: { 'Content-Type': undefined },
    });
  },

  // Downloads any document by its ID alone - works for KYC documents
  // that have no application. Used by the Admin KYC review and the
  // applicant's KYC page to view uploaded files.
  downloadDocumentById: (docId) =>
    api.get(`/api/documents/${docId}/download`, {
      responseType: 'blob',
    }),

  getDocumentsByBusiness: (businessId) => api.get(`/api/sme-businesses/${businessId}/documents`),

  // Admin: hard-delete a REJECTED application (and its documents) from the DB
  deleteRejectedApplication: (id) => api.delete(`/api/loan-applications/${id}`),

  // Loan Products
  createProduct: (data, createdById) => api.post(`/api/loan-products?createdById=${createdById}`, data),
  getProducts: (status) => api.get('/api/loan-products', { params: status ? { status } : {} }),
};

export default smeService;
