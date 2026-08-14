import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import smeService from '../../services/smeService';

const REQUIRED_DOCS = ['PAN_CARD', 'GST_RETURNS', 'AUDITED_FINANCIALS'];
const DOC_LABELS = {
  PAN_CARD: 'PAN Card',
  GST_RETURNS: 'GST Returns',
  AUDITED_FINANCIALS: 'Financial Statement',
};

const KYC_BADGE = { Verified: 'success', Rejected: 'danger', Pending: 'neutral' };

export default function KycReview() {
  const [businesses, setBusinesses] = useState([]);
  const [businessId, setBusinessId] = useState('');
  const [promoters, setPromoters] = useState([]);
  const [documents, setDocuments] = useState([]);
  const [remarks, setRemarks] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const loadBusinesses = () => {
    smeService.getAllBusinesses().then((res) => setBusinesses(res.data.data));
  };

  useEffect(() => {
    loadBusinesses();
  }, []);

  useEffect(() => {
    setError('');
    setSuccess('');
    setRemarks('');

    if (!businessId) {
      setPromoters([]);
      setDocuments([]);
      return;
    }

    smeService.getPromoters(businessId).then((res) => setPromoters(res.data.data));
    smeService.getDocumentsByBusiness(businessId).then((res) => setDocuments(res.data.data));
  }, [businessId]);

  const business = businesses.find((b) => String(b.businessId) === String(businessId));

  const isReadyToVerify = REQUIRED_DOCS.every((reqDoc) =>
    documents.some((d) => d.documentType === reqDoc)
  );

  const handleViewDoc = (doc) => {
    smeService.downloadDocumentById(doc.docId)
      .then((res) => {
        const url = window.URL.createObjectURL(res.data);
        window.open(url, '_blank');
        setTimeout(() => window.URL.revokeObjectURL(url), 10000);
      })
      .catch((err) => {
        setError(err.response?.data?.message || 'Could not open document.');
      });
  };

  const handleDecision = (status) => {
    setError('');
    setSuccess('');

    smeService.updateKycStatus(businessId, status, status === 'Rejected' ? remarks : undefined)
      .then(() => {
        setSuccess(`KYC ${status.toLowerCase()} for ${business.businessName}.`);
        setRemarks('');
        loadBusinesses();
      })
      .catch((err) => {
        setError(err.response?.data?.message || 'Could not update KYC status.');
      });
  };

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="ADMIN" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Admin Console"
            title="KYC Review"
            subtitle="A business must have Verified KYC before its applications can be submitted for review."
          />

          <div className="mb-4">
            <label className="bk-label">Business</label>
            <select className="form-select bk-input" value={businessId} onChange={(e) => setBusinessId(e.target.value)}>
              <option value="">-- Select --</option>
              {businesses.map((b) => (
                <option key={b.businessId} value={b.businessId}>
                  {b.businessName} - {b.kycStatus}
                </option>
              ))}
            </select>
          </div>

          {error && <div className="alert alert-danger">{error}</div>}
          {success && <div className="alert alert-success">{success}</div>}

          {business && (
            <div className="bk-card p-4">
              <div className="d-flex justify-content-between align-items-start mb-3">
                <div>
                  <h5>{business.businessName}</h5>
                  <p className="text-muted mb-0">
                    {business.registrationNumber} &middot; {business.entityType?.replaceAll('_', ' ')} &middot;{' '}
                    {business.industry} &middot; {business.yearsInOperation} yrs
                  </p>
                </div>
                <div className={`badge text-bg-${KYC_BADGE[business.kycStatus] || 'neutral'}`}>
                  {business.kycStatus}
                </div>
              </div>

              {/* Promoters table */}
              <h6 className="bk-label mb-2">Promoters</h6>
              {promoters.length === 0 ? (
                <p className="text-muted mb-3 small">No promoters on file.</p>
              ) : (
                <table className="table bk-table mb-3">
                  <thead>
                    <tr><th>Name</th><th>ID Ref</th><th>Shareholding</th><th>Credit Score</th></tr>
                  </thead>
                  <tbody>
                    {promoters.map((p) => (
                      <tr key={p.promoterId}>
                        <td>{p.name}</td>
                        <td>{p.nationalIdRef}</td>
                        <td>{p.shareholdingPercent}%</td>
                        <td>{p.creditScore}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}

              {/*Documents Table */}
              <h6 className="bk-label mb-2">Required Documents</h6>
              <table className="table bk-table mb-3">
                <tbody>
                  {REQUIRED_DOCS.map((requiredType) => {
                    const uploadedDoc = documents.find((doc) => doc.documentType === requiredType);

                    const isUploaded = Boolean(uploadedDoc);
                    const canBeViewed = isUploaded && Boolean(uploadedDoc.filePath);
                    const label = DOC_LABELS[requiredType];

                    return (
                      <tr key={requiredType}>
                        <td className="align-middle">
                          {isUploaded && (
                            <div className="text-success">
                              <i className="bi bi-check-circle-fill me-2"></i> {label}
                            </div>
                          )}
                          {!isUploaded && (
                            <div className="text-danger">
                              <i className="bi bi-x-circle-fill me-2"></i> {label} — Not uploaded
                            </div>
                          )}
                        </td>
                        <td className="text-end align-middle">
                          {canBeViewed && (
                            <button className="btn btn-bk-outline btn-sm" onClick={() => handleViewDoc(uploadedDoc)}>
                              <i className="bi bi-eye me-1"></i> View
                            </button>
                          )}
                          {isUploaded && !canBeViewed && (
                            <div className="text-muted">No file attached</div>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>

              <div className="row g-2 align-items-end">

                <div className="col-md-4 d-flex gap-2">
                  <button
                    className="btn btn-bk-primary flex-grow-1"
                    disabled={!isReadyToVerify}
                    onClick={() => handleDecision('Verified')}
                  >
                    Verify
                  </button>
                  <button className="btn btn-bk-outline flex-grow-1" onClick={() => handleDecision('Rejected')}>
                    Reject
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}