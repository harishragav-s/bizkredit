import SmartAmountInput from '../../components/SmartAmountInput';
import React, { useEffect, useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import smeService from '../../services/smeService';
import collateralService from '../../services/collateralService';
import { useAuth } from '../../context/AuthContext';
import { formatINR } from '../../utils/currency';

const ASSET_TYPES = ['PROPERTY', 'PLANT', 'MACHINERY', 'RECEIVABLES', 'GOLD', 'SECURITIES', 'FD', 'VEHICLE', 'OTHER'];
const emptyAsset = () => ({ assetType: 'PROPERTY', description: '', ownerName: '', estimatedValue: '', location: '' });

export default function ApplicationWizard() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const [step, setStep] = useState(1);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Step 1 — Business
  const [businesses, setBusinesses] = useState([]);
  const [businessesLoading, setBusinessesLoading] = useState(true);
  const [selectedBusinessId, setSelectedBusinessId] = useState('');

  // Step 2 — Loan product + amount
  const [products, setProducts] = useState([]);
  const [productsLoading, setProductsLoading] = useState(true);
  const [productsError, setProductsError] = useState('');
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [application, setApplication] = useState({ requestedAmount: '', tenure: '', purpose: '' });

  // Step 3 — Collateral disclosure (optional, stored locally, shown to evaluator)
  const [assets, setAssets] = useState([emptyAsset()]);
  const [skipCollateral, setSkipCollateral] = useState(false);

  useEffect(() => {
    smeService.getMyBusinesses(user.userId)
      .then((res) => setBusinesses(res.data.data))
      .catch(() => {})
      .finally(() => setBusinessesLoading(false));
    smeService.getProducts('ACTIVE')
      .then((res) => setProducts(res.data.data))
      .catch((err) => setProductsError(err.response?.data?.message || 'Could not load loan products.'))
      .finally(() => setProductsLoading(false));
  }, [user.userId]);

  const selectedBusiness = businesses.find((b) => String(b.businessId) === String(selectedBusinessId));

  const setAssetField = (idx, field, value) =>
    setAssets((prev) => prev.map((a, i) => i === idx ? { ...a, [field]: value } : a));

  // Step 1 → 2 — KYC must be Verified before proceeding
  const handleBusinessNext = (e) => {
    e.preventDefault();
    setError('');
    if (!selectedBusinessId) { setError('Select a business first.'); return; }
    if (selectedBusiness && selectedBusiness.kycStatus !== 'Verified') {
      setError('KYC must be Verified before you can apply for a loan. Please complete your KYC first.');
      return;
    }
    setStep(2);
  };

  // Step 2 → 3
  const handleProductNext = (e) => {
    e.preventDefault();
    setError('');
    if (!application.requestedAmount || !application.tenure) { setError('Fill in amount and tenure.'); return; }
    setStep(3);
  };

  // Step 3 → Submit
  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(''); setSubmitting(true);
    let createdAppId = null;
    try {
      const res = await smeService.createApplication(selectedBusinessId, {
        productType: selectedProduct.productType,
        requestedAmount: application.requestedAmount,
        tenure: application.tenure,
        purpose: application.purpose,
        applicantUserId: user.userId,
      });
      createdAppId = res.data.data.applicationId;

      // Persist collateral disclosures to collateral-service so the
      // Collateral Evaluator can actually see them. These were previously
      // written to localStorage, which meant they only ever existed in
      // THIS browser on THIS machine - the evaluator, logging in
      // elsewhere, saw nothing at all and had to re-key everything by
      // hand. Each one is saved as a DISCLOSED collateral record (the
      // applicant's own estimate, not yet a formal valuation).
      if (!skipCollateral) {
        const filledAssets = assets.filter((a) => a.description.trim());
        for (const a of filledAssets) {
          try {
            await collateralService.registerCollateral(createdAppId, {
              assetType: a.assetType,
              description: a.location ? `${a.description} (${a.location})` : a.description,
              ownerName: a.ownerName || undefined,
              marketValue: a.estimatedValue || undefined,
            });
          } catch {
            // Non-fatal: the application itself must still submit even if
            // one collateral row fails - the applicant can re-disclose
            // later rather than lose the whole application.
          }
        }
      }

      await smeService.submitApplication(createdAppId);
      navigate('/applicant/tracker');
    } catch (err) {
      if (createdAppId) {
        setError((err.response?.data?.message || 'Saved as draft but could not submit.') + ' View in Application Tracker.');
        setTimeout(() => navigate('/applicant/tracker'), 2500);
      } else {
        setError(err.response?.data?.message || 'Could not create application.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const stepTitles = ['Select Business', 'Loan Details', 'Collateral Disclosure'];

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="SME_APPLICANT" />
        <div className="bk-content" >
          <PageHeader
            eyebrow={`Step ${step} of 3`}
            title="New Loan Application"
            subtitle={stepTitles[step - 1]}
          />

          {/* Step progress */}
          <div className="d-flex align-items-center gap-0 mb-4" >
            {stepTitles.map((title, i) => {
              const idx = i + 1;
              const done = idx < step, current = idx === step;
              return (
                <React.Fragment key={idx}>
                  <div className="d-flex flex-column align-items-center" style={{ minWidth: '100px' }}>
                    <div style={{
                      width: '32px', height: '32px', borderRadius: '50%',
                      background: done ? 'var(--bk-navy)' : current ? 'var(--bk-gold)' : '#e8e8e8',
                      color: done || current ? '#fff' : '#bbb',
                      display: 'flex', alignItems: 'center', justifyContent: 'center',
                      border: current ? '3px solid var(--bk-navy)' : 'none',
                    }}>
                      {done ? '✓' : idx}
                    </div>
                    <div style={{ textAlign: 'center', marginTop: '3px', color: current ? 'var(--bk-navy)' : '#aaa', fontWeight: current ? 700 : 400, textTransform: 'uppercase' }}>
                      {title}
                    </div>
                  </div>
                  {i < stepTitles.length - 1 && (
                    <div style={{ flex: 1, height: '2px', background: idx < step ? 'var(--bk-navy)' : '#e0e0e0', marginBottom: '18px' }} />
                  )}
                </React.Fragment>
              );
            })}
          </div>

          {error && <div className="alert alert-danger">{error}</div>}

          {/* ── Step 1: Business ── */}
          {step === 1 && (
            <>
              {businessesLoading && <p className="text-muted">Loading your businesses...</p>}
              {!businessesLoading && businesses.length === 0 && (
                <div className="bk-card p-4">
                  <h5 >No registered business</h5>
                  <p className="text-muted" >Register your business and complete KYC before applying.</p>
                  <Link to="/applicant/register-business" className="btn btn-bk-primary">
                    <i className="bi bi-building-add me-1"></i>Register a Business
                  </Link>
                </div>
              )}
              {!businessesLoading && businesses.length > 0 && (
                <form onSubmit={handleBusinessNext} className="bk-card p-4">
                  <div className="mb-3">
                    <label className="bk-label">Business</label>
                    <select className="form-select bk-input" value={selectedBusinessId}
                      onChange={(e) => setSelectedBusinessId(e.target.value)}>
                      <option value="">-- Select your business --</option>
                      {businesses.map((b) => (
                        <option key={b.businessId} value={b.businessId}>
                          {b.businessName} — KYC {b.kycStatus}
                        </option>
                      ))}
                    </select>
                  </div>
                  {selectedBusiness && selectedBusiness.kycStatus === 'Verified' && (
                    <div className="alert alert-success py-2 px-3 d-flex align-items-center gap-2 mb-3">
                      <i className="bi bi-shield-check-fill"></i>
                      <span><strong>KYC Verified.</strong> This business is eligible to apply for a loan.</span>
                    </div>
                  )}
                  {selectedBusiness && selectedBusiness.kycStatus !== 'Verified' && (
                    <div className="alert alert-warning py-2 px-3 mb-3">
                      <div className="d-flex align-items-center gap-2 mb-1">
                        <i className="bi bi-shield-exclamation-fill"></i>
                        <strong>KYC {selectedBusiness.kycStatus} — Loan application blocked</strong>
                      </div>
                      <p className="mb-2 small">
                        Your business KYC must be <strong>Verified</strong> before you can apply for a loan.
                        {selectedBusiness.kycStatus === 'Rejected' && selectedBusiness.kycRemarks && (
                          <span> Rejection reason: <em>{selectedBusiness.kycRemarks}</em></span>
                        )}
                      </p>
                      <Link to="/applicant/kyc" className="btn btn-sm btn-warning">
                        <i className="bi bi-arrow-right-circle me-1"></i>Complete KYC Now
                      </Link>
                    </div>
                  )}
                  <div className="d-flex gap-2">
                    <button
                      type="submit"
                      className="btn btn-bk-primary"
                      disabled={selectedBusiness && selectedBusiness.kycStatus !== 'Verified'}
                    >
                      Next: Loan Details →
                    </button>
                    <Link to="/applicant/register-business" className="btn btn-bk-outline">Register another business</Link>
                  </div>
                </form>
              )}
            </>
          )}

          {/* ── Step 2: Loan product + amount ── */}
          {step === 2 && (
            <>
              <button type="button" className="btn btn-link p-0 mb-3" onClick={() => { setStep(1); setSelectedProduct(null); }}>
                ← Back
              </button>
              {productsLoading && <p className="text-muted">Loading loan products...</p>}
              {productsError && <div className="alert alert-danger">{productsError}</div>}
              {!productsLoading && !productsError && products.length === 0 && (
                <div className="bk-empty"><i className="bi bi-inbox"></i>No loan products available. Contact administrator.</div>
              )}
              {!productsLoading && !productsError && products.length > 0 && !selectedProduct && (
                <div className="row g-3">
                  {products.map((p) => (
                    <div className="col-md-6" key={p.productId}>
                      <button type="button" className="bk-card p-3 w-100 text-start border-0" style={{ cursor: 'pointer' }}
                        onClick={() => {
                          setSelectedProduct(p);
                          // Pre-fill tenure from the product's own minimum so
                          // the applicant starts from a valid value for THIS
                          // product rather than a blank box they have to
                          // guess at (and that the backend would reject if
                          // it falls outside the product's min/max).
                          setApplication({ requestedAmount: '', tenure: p.minTenure ? String(p.minTenure) : '', purpose: '' });
                        }}>
                        <h6 className="mb-1 fw-semibold">{p.productName}</h6>
                        <p className="text-muted small mb-2">{p.productType.replaceAll('_', ' ')}</p>
                        <p className="mb-1 small text-muted">{formatINR(p.minAmount)} – {formatINR(p.maxAmount)}</p>
                        <p className="mb-0 text-muted" >
                          {p.minTenure}–{p.maxTenure} months · {p.baseInterestRate}% p.a.
                        </p>
                      </button>
                    </div>
                  ))}
                </div>
              )}
              {selectedProduct && (
                <form onSubmit={handleProductNext} className="bk-card p-4">
                  <div className="d-flex justify-content-between align-items-start mb-3">
                    <div>
                      <h6 className="mb-1" >{selectedProduct.productName}</h6>
                      <p className="text-muted small mb-0">
                        {formatINR(selectedProduct.minAmount)} – {formatINR(selectedProduct.maxAmount)} · {selectedProduct.minTenure}–{selectedProduct.maxTenure} months · {selectedProduct.baseInterestRate}% p.a.
                      </p>
                    </div>
                    <button type="button" className="btn btn-link p-0" onClick={() => setSelectedProduct(null)}>Change</button>
                  </div>
                  <div className="mb-3">
                    <label className="bk-label">Requested Amount</label>
                    <SmartAmountInput min={selectedProduct.minAmount} max={selectedProduct.maxAmount}
                      value={application.requestedAmount}
                      onChange={(e) => setApplication({ ...application, requestedAmount: e.target.value })} />
                  </div>
                  <div className="mb-3">
                    <label className="bk-label">Tenure (months)</label>
                    <input type="number" className="form-control bk-input" required
                      min={selectedProduct.minTenure} max={selectedProduct.maxTenure}
                      value={application.tenure}
                      onChange={(e) => setApplication({ ...application, tenure: e.target.value })} />
                    <div className="form-text">{selectedProduct.minTenure}–{selectedProduct.maxTenure} months</div>
                  </div>
                  <div className="mb-3">
                    <label className="bk-label">Purpose</label>
                    <textarea className="form-control bk-input" rows={2}
                      value={application.purpose}
                      onChange={(e) => setApplication({ ...application, purpose: e.target.value })} />
                  </div>
                  <button type="submit" className="btn btn-bk-primary">Next: Collateral →</button>
                </form>
              )}
            </>
          )}

          {/* ── Step 3: Collateral disclosure ── */}
          {step === 3 && (
            <>
              <button type="button" className="btn btn-link p-0 mb-3" onClick={() => setStep(2)}>← Back</button>

              <div className="bk-card p-3 mb-3" style={{ background: 'var(--bk-paper)', fontSize: '0.85rem' }}>
                <strong>Optional but recommended.</strong> Disclose assets you're willing to pledge as collateral.
                A Collateral Evaluator will formally assess and register the realisable value.
                This information goes directly to the evaluator's register for reference — you don't need to fill it separately.
              </div>

              <form onSubmit={handleSubmit}>
                <div className="bk-card p-4 mb-3">
                  <div className="d-flex justify-content-between align-items-center mb-3">
                    <h6 className="bk-label mb-0" >ASSETS TO PLEDGE</h6>
                    <label className="d-flex align-items-center gap-2" style={{ cursor: 'pointer' }}>
                      <input type="checkbox" checked={skipCollateral} onChange={(e) => setSkipCollateral(e.target.checked)} />
                      Skip — I have no collateral to declare
                    </label>
                  </div>

                  {!skipCollateral && assets.map((asset, idx) => (
                    <div key={idx} className="row g-2 mb-3 pb-3" style={{ borderBottom: idx < assets.length - 1 ? '1px solid var(--bk-line)' : 'none' }}>
                      <div className="col-md-3">
                        <label className="bk-label" >Asset Type</label>
                        <select className="form-select bk-input" value={asset.assetType}
                          onChange={(e) => setAssetField(idx, 'assetType', e.target.value)}>
                          {ASSET_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                        </select>
                      </div>
                      <div className="col-md-5">
                        <label className="bk-label" >Description</label>
                        <input className="form-control bk-input" placeholder="e.g. Residential property at 12 MG Road, 1200 sqft"
                          value={asset.description} onChange={(e) => setAssetField(idx, 'description', e.target.value)} />
                      </div>
                      <div className="col-md-4">
                        <label className="bk-label" >Owner Name</label>
                        <input className="form-control bk-input" value={asset.ownerName}
                          onChange={(e) => setAssetField(idx, 'ownerName', e.target.value)} />
                      </div>
                      <div className="col-md-4">
                        <label className="bk-label" >Your Estimated Value (₹)</label>
                        <input type="number" className="form-control bk-input"
                          placeholder="Self-estimate — evaluator will assess formally"
                          value={asset.estimatedValue} onChange={(e) => setAssetField(idx, 'estimatedValue', e.target.value)} />
                        {asset.estimatedValue && (
                          <div className="form-text">{formatINR(asset.estimatedValue)}</div>
                        )}
                      </div>
                      <div className="col-md-5">
                        <label className="bk-label" >Location / Address</label>
                        <input className="form-control bk-input" placeholder="City, state or full address"
                          value={asset.location} onChange={(e) => setAssetField(idx, 'location', e.target.value)} />
                      </div>
                      <div className="col-md-3 d-flex align-items-end">
                        {assets.length > 1 && (
                          <button type="button" className="btn btn-link text-danger p-0" 
                            onClick={() => setAssets((prev) => prev.filter((_, i) => i !== idx))}>
                            Remove
                          </button>
                        )}
                      </div>
                    </div>
                  ))}

                  {!skipCollateral && (
                    <button type="button" className="btn btn-bk-outline" 
                      onClick={() => setAssets((prev) => [...prev, emptyAsset()])}>
                      <i className="bi bi-plus-lg me-1"></i>Add another asset
                    </button>
                  )}
                </div>

                <button type="submit" className="btn btn-bk-primary" disabled={submitting}>
                  {submitting ? 'Submitting application...' : 'Submit Application'}
                </button>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
