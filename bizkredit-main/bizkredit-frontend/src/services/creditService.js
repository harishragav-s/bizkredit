import api from './api';

const creditService = {
  // Financial Statements
  addStatement: (appId, data) => api.post(`/api/loan-applications/${appId}/financial-statements`, data),
  getStatements: (appId) => api.get(`/api/loan-applications/${appId}/financial-statements`),

  // Credit Proposals
  createProposal: (appId, data) => api.post(`/api/loan-applications/${appId}/credit-proposals`, data),
  submitProposal: (appId, id) =>
    api.patch(`/api/loan-applications/${appId}/credit-proposals/${id}/submit`),
  // Optional status filter (e.g. 'SUBMITTED') is sent as a query param when
  // provided; omitting it preserves the old "fetch everything" behaviour for
  // every other caller that only ever passed appId.
  getProposalsByApplication: (appId, status) =>
    api.get(`/api/loan-applications/${appId}/credit-proposals`, status ? { params: { status } } : undefined),

  // Underwriting Decisions
  makeDecision: (proposalId, data) => api.post(`/api/credit-proposals/${proposalId}/decisions`, data),
  getDecisionByProposal: (proposalId) => api.get(`/api/credit-proposals/${proposalId}/decisions`),

  // Maker-Checker (BP2-17/18) - distinct path from collateral-service's
  // own /api/maker-checker, which covers facility/collateral-side actions.
  submitForApproval: (data) => api.post('/api/credit-maker-checker', data),
};

export default creditService;
