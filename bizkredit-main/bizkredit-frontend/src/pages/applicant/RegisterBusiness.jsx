import SmartAmountInput from '../../components/SmartAmountInput';
import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import smeService from '../../services/smeService';
import { useAuth } from '../../context/AuthContext';
import { nullifyEmptyStrings } from '../../utils/forms';

const ENTITY_TYPES = ['PRIVATE_LIMITED', 'PARTNERSHIP', 'PROPRIETORSHIP', 'LLP', 'OPC'];
const emptyPromoter = () => ({ name: '', nationalIdRef: '', shareholdingPercent: '', creditScore: '' });

export default function RegisterBusiness() {
  const { user } = useAuth();
  const [businesses, setBusinesses] = useState([]);
  const [promotersByBiz, setPromotersByBiz] = useState({});
  const [loadingBiz, setLoadingBiz] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [business, setBusiness] = useState({
    businessName: '',
    registrationNumber: '',
    entityType: 'PRIVATE_LIMITED',
    industry: '',
    yearsInOperation: '',
    annualTurnover: '',
    employeeCount: '',
    // Bank details — filled by applicant so RM can disburse
    beneficiaryName: '',
    beneficiaryAccountNo: '',
    beneficiaryIfsc: '',
    beneficiaryBankName: '',
  });

  const [promoters, setPromoters] = useState([emptyPromoter()]);

  const loadBusinesses = async () => {
    setLoadingBiz(true);
    try {
      const res = await smeService.getMyBusinesses(user.userId);
      const list = res.data.data;
      setBusinesses(list);
      const pmap = {};
      await Promise.all(list.map(async (b) => {
        try {
          const pr = await smeService.getPromoters(b.businessId);
          pmap[b.businessId] = pr.data.data;
        } catch { pmap[b.businessId] = []; }
      }));
      setPromotersByBiz(pmap);
    } catch { /* ignore */ }
    finally { setLoadingBiz(false); }
  };

  useEffect(() => {
    loadBusinesses();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user.userId]);

  const setPromoterField = (idx, field, value) =>
    setPromoters((prev) => prev.map((p, i) => (i === idx ? { ...p, [field]: value } : p)));

  const handleRegister = async (e) => {
    e.preventDefault();
    setError(''); setSuccess('');
    setSubmitting(true);
    try {
      const payload = nullifyEmptyStrings({ ...business, applicantUserId: user.userId });
      const res = await smeService.registerBusiness(payload);
      const newBiz = res.data.data;

      const filledPromoters = promoters.filter((p) => p.name.trim() && p.nationalIdRef.trim());
      for (const p of filledPromoters) {
        try {
          await smeService.addPromoter(newBiz.businessId, nullifyEmptyStrings({
            name: p.name, nationalIdRef: p.nationalIdRef,
            shareholdingPercent: p.shareholdingPercent || null,
            creditScore: p.creditScore || null,
          }));
        } catch { /* continue */ }
      }

      setSuccess(
        `Business "${newBiz.businessName}" registered`
        + (filledPromoters.length ? ` with ${filledPromoters.length} promoter(s)` : '')
        + '. Bank details saved. Next, complete KYC.'
      );
      setBusiness({
        businessName: '', registrationNumber: '', entityType: 'PRIVATE_LIMITED',
        industry: '', yearsInOperation: '', annualTurnover: '', employeeCount: '',
        beneficiaryName: '', beneficiaryAccountNo: '', beneficiaryIfsc: '', beneficiaryBankName: '',
      });
      setPromoters([emptyPromoter()]);
      loadBusinesses();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not register business.');
    } finally { setSubmitting(false); }
  };

  const set = (field) => (e) => setBusiness({ ...business, [field]: e.target.value });

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="SME_APPLICANT" />
        <div className="bk-content" >
          <PageHeader
            eyebrow="Applicant Portal"
            title="Register Business"
            subtitle="Register your business, promoters, and bank details in one step. Bank details are used by the Relationship Manager to disburse approved funds."
          />

          {error && <div className="alert alert-danger">{error}</div>}
          {success && <div className="alert alert-success">{success}</div>}

          {/* Existing businesses */}
          {!loadingBiz && businesses.length > 0 && (
            <div className="mb-4">
              <h6 className="bk-label mb-2" >YOUR REGISTERED BUSINESSES</h6>
              {businesses.map((b) => {
                const promos = promotersByBiz[b.businessId] || [];
                return (
                  <div key={b.businessId} className="bk-card p-3 mb-2">
                    <div className="d-flex justify-content-between align-items-start">
                      <div>
                        <strong >{b.businessName}</strong>
                        <div className="text-muted" >
                          {b.registrationNumber} · {b.entityType?.replaceAll('_', ' ')} · KYC <span className={`badge text-bg-${b.kycStatus === 'Verified' ? 'success' : b.kycStatus === 'Rejected' ? 'danger' : 'warning'}`}>{b.kycStatus}</span>
                        </div>
                        {promos.length > 0 && (
                          <div className="mt-1">
                            Promoters: {promos.map((p) => p.name).join(', ')}
                          </div>
                        )}
                        {/* Show bank details summary */}
                        {b.beneficiaryAccountNo && (
                          <div className="mt-1" >
                            <i className="bi bi-bank me-1"></i>
                            {b.beneficiaryBankName || 'Bank'} · A/C: {b.beneficiaryAccountNo}
                            {b.beneficiaryIfsc && ` · IFSC: ${b.beneficiaryIfsc}`}
                          </div>
                        )}
                        {!b.beneficiaryAccountNo && (
                          <div className="mt-1" >
                            <i className="bi bi-exclamation-triangle me-1"></i>
                            No bank details — RM cannot disburse without these.
                          </div>
                        )}
                      </div>
                      <Link to="/applicant/kyc" className="btn btn-bk-outline btn-sm">
                        {b.kycStatus === 'Verified' ? 'View KYC' : 'Complete KYC'}
                      </Link>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* Registration form */}
          <h6 className="bk-label mb-2" >REGISTER A NEW BUSINESS</h6>
          <form onSubmit={handleRegister} className="bk-card p-4">

            {/* Business details */}
            <div className="row g-3 mb-3">
              <div className="col-md-6">
                <label className="bk-label">Business Name</label>
                <input className="form-control bk-input" required value={business.businessName} onChange={set('businessName')} />
              </div>
              <div className="col-md-6">
                <label className="bk-label">Registration Number</label>
                <input className="form-control bk-input" required
                  placeholder="CIN / GSTIN / Udyam number"
                  value={business.registrationNumber} onChange={set('registrationNumber')} />
              </div>
              <div className="col-md-4">
                <label className="bk-label">Entity Type</label>
                <select className="form-select bk-input" value={business.entityType} onChange={set('entityType')}>
                  {ENTITY_TYPES.map((t) => <option key={t} value={t}>{t.replaceAll('_', ' ')}</option>)}
                </select>
              </div>
              <div className="col-md-4">
                <label className="bk-label">Industry</label>
                <input className="form-control bk-input" placeholder="e.g. Manufacturing, Retail"
                  value={business.industry} onChange={set('industry')} />
              </div>
              <div className="col-md-4">
                <label className="bk-label">Years in Operation</label>
                <input type="number" className="form-control bk-input"
                  value={business.yearsInOperation} onChange={set('yearsInOperation')} />
              </div>
              <div className="col-md-6">
                <label className="bk-label">Annual Turnover</label>
                <SmartAmountInput value={business.annualTurnover} onChange={set('annualTurnover')} />
              </div>
              <div className="col-md-6">
                <label className="bk-label">Employee Count</label>
                <input type="number" className="form-control bk-input"
                  value={business.employeeCount} onChange={set('employeeCount')} />
              </div>
            </div>

            <hr />

            {/* Bank details — applicant fills, RM reads for disbursement */}
            <div className="mb-1">
              <div className="bk-label" >
                DISBURSEMENT BANK DETAILS
              </div>
              <p className="text-muted mb-3" >
                Where approved loan funds will be credited. Your Relationship Manager will use these details to transfer disbursements — you don't need to provide them again later.
              </p>
            </div>
            <div className="row g-3 mb-3">
              <div className="col-md-6">
                <label className="bk-label">Account Holder Name</label>
                <input className="form-control bk-input" placeholder="Name as per bank records"
                  value={business.beneficiaryName} onChange={set('beneficiaryName')} />
              </div>
              <div className="col-md-6">
                <label className="bk-label">Account Number</label>
                <input className="form-control bk-input" placeholder="Bank account number"
                  value={business.beneficiaryAccountNo} onChange={set('beneficiaryAccountNo')} />
              </div>
              <div className="col-md-4">
                <label className="bk-label">IFSC Code</label>
                <input className="form-control bk-input" placeholder="e.g. SBIN0001234"
                  value={business.beneficiaryIfsc} onChange={set('beneficiaryIfsc')} />
              </div>
              <div className="col-md-8">
                <label className="bk-label">Bank &amp; Branch</label>
                <input className="form-control bk-input" placeholder="e.g. State Bank of India, Anna Nagar Branch, Chennai"
                  value={business.beneficiaryBankName} onChange={set('beneficiaryBankName')} />
              </div>
            </div>

            <hr />

            {/* Promoters */}
            <div className="d-flex justify-content-between align-items-center mb-2">
              <label className="bk-label mb-0">
                Promoters
                <span className="text-muted ms-1" style={{ textTransform: 'none' }}>(owners / directors)</span>
              </label>
              <button type="button" className="btn btn-bk-outline btn-sm"
                onClick={() => setPromoters((p) => [...p, emptyPromoter()])}>
                <i className="bi bi-plus"></i> Add another
              </button>
            </div>
            {promoters.map((p, idx) => (
              <div key={idx} className="row g-2 align-items-end mb-2">
                <div className="col-md-3">
                  {idx === 0 && <label className="bk-label" >Name</label>}
                  <input className="form-control bk-input" 
                    value={p.name} onChange={(e) => setPromoterField(idx, 'name', e.target.value)} />
                </div>
                <div className="col-md-3">
                  {idx === 0 && <label className="bk-label" >ID Reference</label>}
                  <input className="form-control bk-input"  placeholder="PAN / Aadhaar ref"
                    value={p.nationalIdRef} onChange={(e) => setPromoterField(idx, 'nationalIdRef', e.target.value)} />
                </div>
                <div className="col-md-2">
                  {idx === 0 && <label className="bk-label" >Share %</label>}
                  <input type="number" className="form-control bk-input" 
                    value={p.shareholdingPercent} onChange={(e) => setPromoterField(idx, 'shareholdingPercent', e.target.value)} />
                </div>
                <div className="col-md-2">
                  {idx === 0 && <label className="bk-label" >Credit Score</label>}
                  <input type="number" className="form-control bk-input" 
                    value={p.creditScore} onChange={(e) => setPromoterField(idx, 'creditScore', e.target.value)} />
                </div>
                <div className="col-md-2">
                  {promoters.length > 1 && (
                    <button type="button" className="btn btn-link text-danger p-0" 
                      onClick={() => setPromoters((prev) => prev.filter((_, i) => i !== idx))}>
                      Remove
                    </button>
                  )}
                </div>
              </div>
            ))}
            <div className="form-text mb-3">Optional. Fill name + ID reference to include a promoter.</div>

            <button type="submit" className="btn btn-bk-primary" disabled={submitting}>
              {submitting ? 'Registering...' : 'Register Business'}
            </button>
          </form>

          <div className="mt-4 d-flex gap-2">
            <Link to="/applicant/kyc" className="btn btn-bk-outline small text-muted">
              Next: Complete KYC
            </Link>
            <Link to="/applicant/apply" className="btn btn-bk-outline small text-muted">
              Apply for a Loan
            </Link>
          </div>
        </div>
      </div>
    </div>
  );
}
