import React, { useEffect, useState, useRef } from 'react';
import { Link } from 'react-router-dom';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import smeService from '../../services/smeService';
import { useAuth } from '../../context/AuthContext';

// The three documents KYC verification requires (must match the
// backend's REQUIRED_KYC_DOCUMENTS in SMELoanService).
const REQUIRED_KYC_DOCS = [
  { type: 'PAN_CARD', label: 'PAN Card' },
  { type: 'GST_RETURNS', label: 'GST Returns' },
  { type: 'AUDITED_FINANCIALS', label: 'Financial Statement' },
];

const KYC_BADGE = { Verified: 'success', Rejected: 'danger', Pending: 'warning' };


export default function MyBusinessKyc() {
  const { user } = useAuth();
  const [businesses, setBusinesses] = useState([]);
  const [docsByBusiness, setDocsByBusiness] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [uploadingKey, setUploadingKey] = useState(null);
  const fileInputs = useRef({});

  const load = async () => {
    setLoading(true);
    try {
      const bizRes = await smeService.getMyBusinesses(user.userId);
      const validBiz = bizRes.data.data;
      setBusinesses(validBiz);

      // Fetch documents per business for the checklist.
      const docsMap = {};
      await Promise.all(
        validBiz.map(async (b) => {
          try {
            const dRes = await smeService.getDocumentsByBusiness(b.businessId);
            docsMap[b.businessId] = dRes.data.data;
          } catch {
            docsMap[b.businessId] = [];
          }
        })
      );
      setDocsByBusiness(docsMap);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not load your business KYC.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user.userId]);

  const handleUpload = async (business, docType) => {
    setError('');
    setSuccess('');
    const key = `${business.businessId}-${docType}`;
    const file = fileInputs.current[key]?.files?.[0];
    if (!file) {
      setError('Choose a file first.');
      return;
    }
    setUploadingKey(key);
    try {
      // KYC documents now attach directly to the business - no loan
      // application needed first.
      await smeService.uploadBusinessDocumentFile(business.businessId, {
        documentType: docType,
        financialYear: String(new Date().getFullYear()),
        file,
      });
      setSuccess(`Uploaded ${docType.replaceAll('_', ' ')}.`);
      await load();
    } catch (err) {
      setError(err.response?.data?.message || 'Upload failed.');
    } finally {
      setUploadingKey(null);
    }
  };

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="SME_APPLICANT" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Applicant Portal"
            title="My Business & KYC"
            subtitle="Get your business KYC-verified — upload the required documents here. An Admin reviews them, and you'll be notified of the outcome."
          />

          {error && <div className="alert alert-danger">{error}</div>}
          {success && <div className="alert alert-success">{success}</div>}
          {loading && <p className="text-muted">Loading...</p>}

          {!loading && businesses.length === 0 && (
            <div className="bk-card p-4" >
              <h5 >No business yet</h5>
              <p className="text-muted" >
                Your business is created when you start your first loan application. Start one, then come back here to complete KYC.
              </p>
              <Link to="/applicant/apply" className="btn btn-bk-primary small text-muted">
                <i className="bi bi-file-earmark-plus me-1"></i>Start an Application
              </Link>
            </div>
          )}

          {!loading && businesses.map((b) => {
            const uploaded = new Set((docsByBusiness[b.businessId] || []).map((d) => d.documentType));
            const missing = REQUIRED_KYC_DOCS.filter((d) => !uploaded.has(d.type));
            const allUploaded = missing.length === 0;

            return (
              <div key={b.businessId} className="bk-card p-4 mb-4" >
                <div className="d-flex justify-content-between align-items-start mb-3">
                  <div>
                    <h5 style={{ margin: 0 }}>{b.businessName}</h5>
                    <p className="text-muted mb-0 small">
                      {b.registrationNumber} &middot; {b.entityType?.replaceAll('_', ' ')} &middot; {b.industry}
                    </p>
                  </div>
                  <span className={`badge text-bg-${KYC_BADGE[b.kycStatus] || 'neutral'}`}>
                    KYC {b.kycStatus}
                  </span>
                </div>

                {b.kycStatus === 'Verified' && (
                  <div className="alert alert-success py-2 px-3 small text-muted">
                    <i className="bi bi-shield-check me-1"></i>
                    Your business is KYC-verified. Your applications can be submitted for review.
                  </div>
                )}

                {b.kycStatus === 'Rejected' && (
                  <div className="alert alert-danger py-2 px-3 small text-muted">
                    <strong>KYC was rejected.</strong>{b.kycRemarks ? ` Reason: ${b.kycRemarks}` : ''} Re-upload the documents below; an Admin will re-review.
                  </div>
                )}

                {b.kycStatus !== 'Verified' && (
                  <>
                    <h6 className="bk-label mb-2 mt-3" >Required KYC Documents</h6>
                    <table className="table bk-table">
                      <tbody>
                        {REQUIRED_KYC_DOCS.map((doc) => {
                          const isUploaded = uploaded.has(doc.type);
                          const key = `${b.businessId}-${doc.type}`;
                          return (
                            <tr key={doc.type}>
                              <td className="align-middle">
                                {isUploaded ? (
                                  <span className="text-success"><i className="bi bi-check-circle-fill me-2"></i>{doc.label}</span>
                                ) : (
                                  <span className="text-danger"><i className="bi bi-x-circle-fill me-2"></i>{doc.label}</span>
                                )}
                              </td>
                              <td className="text-end">
                                {isUploaded ? (
                                  <span className="text-muted" >Uploaded</span>
                                ) : (
                                  <div className="d-flex gap-2 justify-content-end align-items-center">
                                    <input
                                      type="file"
                                      ref={(el) => (fileInputs.current[key] = el)}
                                      style={{ maxWidth: '190px' }}
                                    />
                                    <button
                                      className="btn btn-bk-outline btn-sm"
                                      disabled={uploadingKey === key}
                                      onClick={() => handleUpload(b, doc.type)}
                                    >
                                      {uploadingKey === key ? 'Uploading...' : 'Upload'}
                                    </button>
                                  </div>
                                )}
                              </td>
                            </tr>
                          );
                        })}
                      </tbody>
                    </table>

                    {allUploaded ? (
                      <div className="alert alert-info py-2 px-3 mb-0 small text-muted">
                        <i className="bi bi-hourglass-split me-1"></i>
                        All required documents are uploaded. An Admin will review and verify your KYC — you'll be notified of the outcome.
                      </div>
                    ) : (
                      <p className="text-muted mb-0" >
                        Upload all {REQUIRED_KYC_DOCS.length} documents. Once complete, an Admin reviews them and verifies your KYC.
                      </p>
                    )}
                  </>
                )}
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
