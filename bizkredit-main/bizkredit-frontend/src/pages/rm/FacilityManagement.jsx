import SmartAmountInput from '../../components/SmartAmountInput';
import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import smeService from '../../services/smeService';
import creditService from '../../services/creditService';
import collateralService from '../../services/collateralService';
import { useAuth } from '../../context/AuthContext';
import { formatINR } from '../../utils/currency';

const PRODUCT_TYPES = ['TERM_LOAN', 'WORKING_CAPITAL_CC', 'OVERDRAFT_FACILITY', 'INVOICE_FINANCING', 'EQUIPMENT_LOAN'];
const PAYMENT_METHODS = ['BANK_TRANSFER', 'NEFT', 'RTGS', 'IMPS', 'CHEQUE', 'AUTO_DEBIT', 'CASH'];

export default function FacilityManagement() {
  const { user } = useAuth();
  // All-facilities overview
  const [allFacilities, setAllFacilities] = useState([]);
  const [businessBankDetails, setBusinessBankDetails] = useState(null);
  const [drawdownsByFacility, setDrawdownsByFacility] = useState({});
  const [loadingAll, setLoadingAll] = useState(true);

  // Create form
  const [creatingNew, setCreatingNew] = useState(false);
  const [applications, setApplications] = useState([]);
  const [businesses, setBusinesses] = useState([]);
  const [approvedAmount, setApprovedAmount] = useState(null);
  const [decisionRef, setDecisionRef] = useState(null);
  const [form, setForm] = useState({
    applicationId: '', businessId: '', productType: 'TERM_LOAN',
    sanctionedLimit: '', interestRate: '', expiryDate: '',
    // bank details read from business — not entered by RM
  });

  // Per-facility management
  const [facility, setFacility] = useState(null);
  const [drawdowns, setDrawdowns] = useState([]);
  const [repayments, setRepayments] = useState([]);
  const [drawdownAmount, setDrawdownAmount] = useState('');
  const [drawdownPurpose, setDrawdownPurpose] = useState('');
  const [repaymentTarget, setRepaymentTarget] = useState(null);
  const [repaymentForm, setRepaymentForm] = useState({ amount: '', paymentMethod: 'BANK_TRANSFER', referenceNumber: '' });
  const [confirmDisburseId, setConfirmDisburseId] = useState(null); // drawdown awaiting transfer confirmation

  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const loadAllFacilities = async () => {
    setLoadingAll(true);
    try {
      const res = await collateralService.getAllFacilities();
      const facs = res.data.data || [];
      setAllFacilities(facs);
      // Load drawdowns for every facility to show in table
      const ddMap = {};
      await Promise.all(facs.map(async (f) => {
        try {
          const dRes = await collateralService.getDrawdowns(f.facilityId);
          ddMap[f.facilityId] = dRes.data.data || [];
        } catch { ddMap[f.facilityId] = []; }
      }));
      setDrawdownsByFacility(ddMap);
    } catch { /* silent */ }
    finally { setLoadingAll(false); }
  };

  useEffect(() => {
    loadAllFacilities();
    smeService.getApplications({ status: 'SANCTIONED' }).then((res) => setApplications(res.data.data || []));
    smeService.getAllBusinesses().then((res) => setBusinesses(res.data.data || []));
  }, []);

  useEffect(() => {
    if (!form.applicationId) { setApprovedAmount(null); setDecisionRef(null); return; }
    creditService.getProposalsByApplication(form.applicationId).then(async (res) => {
      const proposals = res.data.data;
      if (!proposals.length) { setApprovedAmount(null); setDecisionRef(null); return; }
      try {
        const dRes = await creditService.getDecisionByProposal(proposals[proposals.length - 1].proposalId);
        const decision = dRes.data.data;
        setApprovedAmount(decision.sanctionedAmount);
        setDecisionRef(decision);

        // Expiry = decision date + approved tenure (months). RM can still
        // override it below - this is a starting point, not a lock.
        let computedExpiry = '';
        if (decision.decisionDate && decision.tenure) {
          const d = new Date(decision.decisionDate);
          d.setMonth(d.getMonth() + Number(decision.tenure));
          computedExpiry = d.toISOString().slice(0, 10);
        }

        const app = applications.find((a) => String(a.applicationId) === String(form.applicationId));

        // Auto-fill every term the underwriting decision already determined -
        // amount, rate, tenure-derived expiry, and product type from the
        // sanctioned application - so creating a facility is select + confirm,
        // not re-typing numbers underwriting already approved.
        setForm((f) => ({
          ...f,
          sanctionedLimit: f.sanctionedLimit || String(decision.sanctionedAmount || ''),
          interestRate: f.interestRate || String(decision.approvedRate || ''),
          expiryDate: f.expiryDate || computedExpiry,
          productType: app?.productType || f.productType,
        }));
      } catch { setApprovedAmount(null); setDecisionRef(null); }
    }).catch(() => { setApprovedAmount(null); setDecisionRef(null); });
  }, [form.applicationId, applications]);

  useEffect(() => {
    if (!form.applicationId) return;
    const app = applications.find((a) => String(a.applicationId) === String(form.applicationId));
    if (app?.businessId) {
      setForm((f) => ({ ...f, businessId: String(app.businessId) }));
      // Load bank details from the business so RM can see them for transfer
      smeService.getBusiness(app.businessId)
        .then((res) => setBusinessBankDetails(res.data.data))
        .catch(() => setBusinessBankDetails(null));
    }
  }, [form.applicationId, applications]);

  const handleChange = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const handleCreateFacility = async (e) => {
    e.preventDefault();
    setError(''); setSuccess('');
    if (approvedAmount != null && Number(form.sanctionedLimit) > Number(approvedAmount)) {
      setError(`Sanctioned limit cannot exceed the underwriting-approved amount of ${formatINR(approvedAmount)}.`);
      return;
    }
    try {
      // eslint-disable-next-line no-unused-vars
      const { applicationId, businessId, ...facilityBody } = form;
      const res = await collateralService.createFacility(applicationId, businessId, facilityBody);
      setSuccess(`Facility #${res.data.data.facilityId} created.`);
      setCreatingNew(false);
      await handleSelectFacility(res.data.data.facilityId);
      loadAllFacilities();
    } catch (err) { setError(err.response?.data?.message || 'Could not create facility.'); }
  };

  const handleSelectFacility = async (facilityId) => {
    setError(''); setSuccess('');
    try {
      const [facRes, ddRes, rpRes] = await Promise.all([
        collateralService.getFacility(facilityId),
        collateralService.getDrawdowns(facilityId),
        collateralService.getRepaymentsByFacility(facilityId),
      ]);
      const fac = facRes.data.data;
      setFacility(fac);
      setDrawdowns(ddRes.data.data || []);

      setRepayments(rpRes.data.data || []);
      // Load borrower's bank details from their business record
      if (fac.businessId) {
        smeService.getBusiness(fac.businessId)
          .then((res) => setBusinessBankDetails(res.data.data))
          .catch(() => setBusinessBankDetails(null));
      }
    } catch (err) { setError(err.response?.data?.message || 'Could not load facility.'); }
  };

  const handleDeleteFacility = async (facilityId) => {
    if (!window.confirm(`Delete Facility #${facilityId}? Only works if nothing has been disbursed yet.`)) return;
    setError(''); setSuccess('');
    try {
      await collateralService.deleteFacility(facilityId);
      setSuccess(`Facility #${facilityId} deleted.`);
      loadAllFacilities();
    } catch (err) { setError(err.response?.data?.message || 'Could not delete facility.'); }
  };

  const handleRequestDrawdown = async (e) => {
    e.preventDefault();
    setError('');
    try {
      const res = await collateralService.requestDrawdown(facility.facilityId, { amount: drawdownAmount, purpose: drawdownPurpose });
      setDrawdowns([...drawdowns, res.data.data]);
      setDrawdownAmount(''); setDrawdownPurpose('');
      setSuccess(`Drawdown of ${formatINR(drawdownAmount)} requested. Click Disburse to credit the borrower.`);
    } catch (err) { setError(err.response?.data?.message || 'Could not request drawdown.'); }
  };

  const handleDisburse = async (drawdownId) => {
    setError('');
    try {
      const res = await collateralService.disburseDrawdown(facility.facilityId, drawdownId);
      setDrawdowns(drawdowns.map((d) => (d.drawdownId === drawdownId ? res.data.data : d)));
      const facRes = await collateralService.getFacility(facility.facilityId);
      setFacility(facRes.data.data);
      loadAllFacilities();
      setSuccess(`Disbursed. Outstanding balance updated to ${formatINR(facRes.data.data.outstandingBalance)}.`);
    } catch (err) { setError(err.response?.data?.message || 'Could not disburse.'); }
  };

  const handleCloseFacility = async () => {
    if (!window.confirm('Close facility? Only allowed when outstanding balance is zero.')) return;
    setError(''); setSuccess('');
    try {
      const res = await collateralService.closeFacility(facility.facilityId);
      setFacility(res.data.data);
      setSuccess(`Facility #${facility.facilityId} closed.`);
      loadAllFacilities();
    } catch (err) { setError(err.response?.data?.message || 'Could not close — ensure outstanding balance is fully repaid.'); }
  };

  const handleRecordRepayment = async (e) => {
    e.preventDefault();
    setError(''); setSuccess('');
    try {
      const res = await collateralService.recordRepayment(repaymentTarget.drawdownId, repaymentForm);
      setRepayments([...repayments, res.data.data]);
      setSuccess(`Repayment of ${formatINR(repaymentForm.amount)} recorded.`);
      setRepaymentForm({ amount: '', paymentMethod: 'BANK_TRANSFER', referenceNumber: '' });
      setRepaymentTarget(null);
      const [facRes, ddRes] = await Promise.all([
        collateralService.getFacility(facility.facilityId),
        collateralService.getDrawdowns(facility.facilityId),
      ]);
      setFacility(facRes.data.data);
      setDrawdowns(ddRes.data.data || []);
      loadAllFacilities();
    } catch (err) { setError(err.response?.data?.message || 'Could not record repayment.'); }
  };

  // Confirms an applicant-submitted repayment claim - this is the moment
  // it actually gets applied to the facility's outstanding balance, not
  // when the applicant first submitted it.
  const handleVerifyRepayment = async (repaymentId) => {
    setError(''); setSuccess('');
    try {
      const res = await collateralService.verifyRepayment(repaymentId, user.userId);
      setRepayments(repayments.map((r) => (r.repaymentId === repaymentId ? res.data.data : r)));
      setSuccess('Repayment verified and applied to the outstanding balance.');
      const [facRes, ddRes] = await Promise.all([
        collateralService.getFacility(facility.facilityId),
        collateralService.getDrawdowns(facility.facilityId),
      ]);
      setFacility(facRes.data.data);
      setDrawdowns(ddRes.data.data || []);
      loadAllFacilities();
    } catch (err) { setError(err.response?.data?.message || 'Could not verify repayment.'); }
  };

  // Stats
  const totalSanctioned = allFacilities.reduce((s, f) => s + (Number(f.sanctionedLimit) || 0), 0);
  const totalOutstanding = allFacilities.reduce((s, f) => s + (Number(f.outstandingBalance) || 0), 0);
  const activeCount = allFacilities.filter((f) => f.status === 'ACTIVE').length;

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="RELATIONSHIP_MANAGER" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Relationship Manager"
            title="Facility Management"
            subtitle="Manage individual credit facilities — create, draw funds, transfer to borrower's bank account, and record repayments. For the aggregate portfolio view see Portfolio Overview."
          />

          {error && <div className="alert alert-danger">{error}</div>}
          {success && <div className="alert alert-success">{success}</div>}

          {/* ── All-facilities overview ── */}
          {!facility && !creatingNew && (
            <>
              {!loadingAll && (
                <div className="row g-3 mb-4" >
                  <div className="col-md-4">
                    <div className="bk-stat-card">
                      <div className="bk-stat-label">Total Sanctioned</div>
                      <div className="bk-stat-value">{formatINR(totalSanctioned)}</div>
                    </div>
                  </div>
                  <div className="col-md-4">
                    <div className="bk-stat-card">
                      <div className="bk-stat-label">Total Outstanding</div>
                      <div className="bk-stat-value">{formatINR(totalOutstanding)}</div>
                    </div>
                  </div>
                  <div className="col-md-4">
                    <div className="bk-stat-card">
                      <div className="bk-stat-label">Active Facilities</div>
                      <div className="bk-stat-value">{activeCount}</div>
                    </div>
                  </div>
                </div>
              )}

              <div className="d-flex justify-content-between align-items-center mb-3" >
                <h6 className="bk-label mb-0" >
                  {loadingAll ? 'Loading...' : `${allFacilities.length} Facilit${allFacilities.length === 1 ? 'y' : 'ies'}`}
                </h6>
                <button className="btn btn-bk-outline" onClick={() => setCreatingNew(true)}>
                  <i className="bi bi-plus-lg me-1"></i>New Facility
                </button>
              </div>

              {!loadingAll && allFacilities.length === 0 && (
                <div className="bk-empty"><i className="bi bi-bank"></i>No facilities yet. Create one from a sanctioned application.</div>
              )}

              {!loadingAll && allFacilities.length > 0 && (
                <table className="table bk-table" >
                  <thead>
                    <tr><th>ID</th><th>Sanctioned</th><th>Disbursed</th><th>Outstanding</th><th>Drawdowns</th><th>Overdue</th><th>Status</th><th></th></tr>
                  </thead>
                  <tbody>
                    {allFacilities.map((f) => {
                      const dds = drawdownsByFacility[f.facilityId] || [];
                      const overdue = dds.filter((d) => d.status === 'OVERDUE').length;
                      return (
                        <tr key={f.facilityId}>
                          <td className="bk-mono">#{f.facilityId}</td>
                          <td>{formatINR(f.sanctionedLimit)}</td>
                          <td>{formatINR(f.disbursedAmount)}</td>
                          <td style={{ fontWeight: f.outstandingBalance > 0 ? 600 : 400, color: f.outstandingBalance > 0 ? 'var(--bk-navy)' : '#888' }}>
                            {formatINR(f.outstandingBalance)}
                          </td>
                          <td >{dds.length > 0 ? `${dds.length} (${dds.filter((d) => d.status === 'DISBURSED').length} active)` : '—'}</td>
                          <td>
                            {overdue > 0
                              ? <span className="badge text-bg-danger">{overdue} overdue</span>
                              : <span className="text-muted" >—</span>
                            }
                          </td>
                          <td><span className={`badge text-bg-${f.status === 'ACTIVE' ? 'success' : 'neutral'}`}>{f.status}</span></td>
                          <td>
                            <button className="btn btn-bk-outline btn-sm"
                              onClick={() => handleSelectFacility(f.facilityId)}>Manage</button>{' '}
                            <button className="btn btn-link text-danger btn-sm"
                              onClick={() => handleDeleteFacility(f.facilityId)}>Delete</button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}
            </>
          )}

          {/* ── Create new facility ── */}
          {creatingNew && (
            <>
              <button className="btn btn-link px-0 mb-2" onClick={() => setCreatingNew(false)}>&larr; Back</button>
              <form onSubmit={handleCreateFacility}>

                {/* Section 1 — which application */}
                <div className="bk-card p-4 mb-3">
                  <div className="d-flex align-items-center gap-2 mb-3">
                    <span className="bk-step-num">1</span>
                    <div>
                      <div className="fw-semibold">Select the sanctioned application</div>
                      <div className="text-muted small">Only applications underwriting has already approved appear here.</div>
                    </div>
                  </div>
                  <div className="row g-3">
                    <div className="col-md-6">
                      <label className="bk-label">Sanctioned Application</label>
                      <select className="form-select bk-input" required value={form.applicationId} onChange={handleChange('applicationId')}>
                        <option value="">-- Select --</option>
                        {applications.map((app) => (
                          <option key={app.applicationId} value={app.applicationId}>
                            #{app.applicationId} - {app.productType?.replaceAll('_', ' ')}
                          </option>
                        ))}
                      </select>
                      {applications.length === 0 && <div className="form-text text-warning">No sanctioned applications found.</div>}
                    </div>
                    <div className="col-md-6">
                      <label className="bk-label">Business</label>
                      <select className="form-select bk-input" required value={form.businessId} onChange={handleChange('businessId')}>
                        <option value="">-- Select --</option>
                        {businesses.map((b) => <option key={b.businessId} value={b.businessId}>{b.businessName}</option>)}
                      </select>
                    </div>
                  </div>
                </div>

                {/* Section 2 — what underwriting approved (read-only reference) */}
                {decisionRef && (
                  <div className="bk-card p-4 mb-3" style={{ borderLeft: '3px solid var(--bk-gold)' }}>
                    <div className="d-flex align-items-center gap-2 mb-3">
                      <span className="bk-step-num">2</span>
                      <div>
                        <div className="fw-semibold">What underwriting approved</div>
                        <div className="text-muted small">Reference only — the facility terms below are pre-filled from this.</div>
                      </div>
                    </div>
                    <div className="row g-3">
                      <div className="col-md-3">
                        <div className="text-muted small">Approved Amount</div>
                        <div><strong>{formatINR(decisionRef.sanctionedAmount)}</strong></div>
                      </div>
                      <div className="col-md-3">
                        <div className="text-muted small">Approved Rate</div>
                        <div><strong>{decisionRef.approvedRate != null ? `${decisionRef.approvedRate}%` : '—'}</strong></div>
                      </div>
                      <div className="col-md-3">
                        <div className="text-muted small">Tenure</div>
                        <div><strong>{decisionRef.tenure != null ? `${decisionRef.tenure} months` : '—'}</strong></div>
                      </div>
                      <div className="col-md-3">
                        <div className="text-muted small">Decision Date</div>
                        <div><strong>{decisionRef.decisionDate || '—'}</strong></div>
                      </div>
                      {decisionRef.specialConditions && (
                        <div className="col-12">
                          <div className="text-muted small">Conditions</div>
                          <div>{decisionRef.specialConditions}</div>
                        </div>
                      )}
                    </div>
                  </div>
                )}

                {/* Section 3 — the actual facility terms being created */}
                <div className="bk-card p-4 mb-3">
                  <div className="d-flex align-items-center gap-2 mb-3">
                    <span className="bk-step-num">3</span>
                    <div>
                      <div className="fw-semibold">Facility terms</div>
                      <div className="text-muted small">Pre-filled from the approval above — adjust only if terms genuinely differ.</div>
                    </div>
                  </div>
                  <div className="row g-3">
                    <div className="col-md-4">
                      <label className="bk-label">Product Type</label>
                      <select className="form-select bk-input" value={form.productType} onChange={handleChange('productType')}>
                        {PRODUCT_TYPES.map((t) => <option key={t} value={t}>{t.replaceAll('_', ' ')}</option>)}
                      </select>
                    </div>
                    <div className="col-md-4">
                      <label className="bk-label">Sanctioned Limit</label>
                      {approvedAmount != null && (
                        <div className={Number(form.sanctionedLimit) > Number(approvedAmount) ? 'text-danger mb-1' : 'text-muted mb-1'}>
                          Approved: <strong>{formatINR(approvedAmount)}</strong> — cannot exceed.
                          {Number(form.sanctionedLimit) > Number(approvedAmount) && ' Current value exceeds this.'}
                        </div>
                      )}
                      <SmartAmountInput
                        required
                        value={form.sanctionedLimit}
                        onChange={handleChange('sanctionedLimit')}
                        max={approvedAmount != null ? approvedAmount : undefined}
                      />
                    </div>
                    <div className="col-md-2">
                      <label className="bk-label">Rate (%)</label>
                      <input type="number" step="0.01" className="form-control bk-input" value={form.interestRate} onChange={handleChange('interestRate')} />
                    </div>
                    <div className="col-md-2">
                      <label className="bk-label">Expiry</label>
                      <input type="date" className="form-control bk-input" value={form.expiryDate} onChange={handleChange('expiryDate')} />
                    </div>
                  </div>
                </div>

                {/* Section 4 — where funds go on disbursement (read-only reference) */}
                <div className="bk-card p-4 mb-3">
                  <div className="d-flex align-items-center gap-2 mb-3">
                    <span className="bk-step-num">4</span>
                    <div>
                      <div className="fw-semibold">Borrower's disbursement bank details</div>
                      <div className="text-muted small">Registered by the applicant — used for fund transfer on disbursement, not editable here.</div>
                    </div>
                  </div>
                  {businessBankDetails?.beneficiaryAccountNo ? (
                    <div className="bk-card p-3" style={{ background: 'var(--bk-paper)', fontSize: '0.85rem' }}>
                      <div className="row g-2">
                        <div className="col-md-6">
                          <span className="bk-label" >ACCOUNT HOLDER</span>
                          <div>{businessBankDetails.beneficiaryName || '—'}</div>
                        </div>
                        <div className="col-md-6">
                          <span className="bk-label" >ACCOUNT NUMBER</span>
                          <div className="bk-mono">{businessBankDetails.beneficiaryAccountNo}</div>
                        </div>
                        <div className="col-md-4">
                          <span className="bk-label" >IFSC</span>
                          <div className="bk-mono">{businessBankDetails.beneficiaryIfsc || '—'}</div>
                        </div>
                        <div className="col-md-8">
                          <span className="bk-label" >BANK & BRANCH</span>
                          <div>{businessBankDetails.beneficiaryBankName || '—'}</div>
                        </div>
                      </div>
                    </div>
                  ) : form.applicationId ? (
                    <div className="alert alert-warning py-2 px-3" >
                      <i className="bi bi-exclamation-triangle me-1"></i>
                      No bank details on file for this business. Ask the applicant to update them in Register Business.
                    </div>
                  ) : (
                    <div className="text-muted" >Select an application above to see bank details.</div>
                  )}
                </div>

                <button type="submit" className="btn btn-bk-primary">Create Facility</button>
              </form>
            </>
          )}

          {/* ── Per-facility management ── */}
          {facility && (
            <div >
              <button className="btn btn-link px-0 mb-2" onClick={() => { setFacility(null); setDrawdowns([]); setRepayments([]); loadAllFacilities(); }}>
                &larr; Back to all facilities
              </button>

              <div className="bk-card p-4 mb-3">
                <div className="d-flex justify-content-between align-items-start mb-3">
                  <h5 className="mb-0" >Facility #{facility.facilityId}</h5>
                  {facility.status === 'ACTIVE' && Number(facility.outstandingBalance) === 0 && (
                    <button className="btn btn-link text-danger p-0"  onClick={handleCloseFacility}>Close Facility</button>
                  )}
                </div>

                {/* Borrower bank details for RM reference during disbursement */}
                {businessBankDetails?.beneficiaryAccountNo && (
                  <div className="bk-card p-3 mb-3" style={{ background: 'var(--bk-paper)', fontSize: '0.83rem' }}>
                    <div className="bk-label mb-1" >
                      BORROWER DISBURSEMENT ACCOUNT — Transfer funds here
                    </div>
                    <div className="row g-1">
                      <div className="col-md-4"><strong>{businessBankDetails.beneficiaryName || '—'}</strong></div>
                      <div className="col-md-4 bk-mono">A/C: {businessBankDetails.beneficiaryAccountNo}</div>
                      <div className="col-md-2 bk-mono">IFSC: {businessBankDetails.beneficiaryIfsc || '—'}</div>
                      <div className="col-md-2" >{businessBankDetails.beneficiaryBankName || '—'}</div>
                    </div>
                  </div>
                )}

                {/* Stats row */}
                <div className="row g-2 mb-3">
                  {[
                    { label: 'Sanctioned', value: formatINR(facility.sanctionedLimit) },
                    { label: 'Disbursed (Credits out)', value: formatINR(facility.disbursedAmount), color: '#27ae60' },
                    { label: 'Outstanding (Balance owed)', value: formatINR(facility.outstandingBalance), color: facility.outstandingBalance > 0 ? '#e74c3c' : '#27ae60' },
                  ].map((stat) => (
                    <div key={stat.label} className="col-md-4">
                      <div className="bk-label" >{stat.label}</div>
                      <div className="bk-stat-value" style={{ fontSize: '1.5rem', color: stat.color || 'var(--bk-navy)' }}>{stat.value}</div>
                    </div>
                  ))}
                </div>

                {/* Drawdown raised by the RM on the borrower's behalf */}
                <div className="border-top pt-3 mb-3">
                  <div className="bk-label mb-2" >
                    RAISE DRAWDOWN ON BORROWER'S BEHALF
                    <span className="text-muted ms-2" style={{ textTransform: 'none', fontSize: '0.72rem' }}>
                      → for phone/branch requests. Borrower-initiated requests appear below automatically.
                    </span>
                  </div>
                  <form onSubmit={handleRequestDrawdown} className="row g-2 align-items-end">
                    <div className="col-md-4">
                      <SmartAmountInput value={drawdownAmount} onChange={(e) => setDrawdownAmount(e.target.value)} max={facility.sanctionedLimit - facility.disbursedAmount} />
                    </div>
                    <div className="col-md-5">
                      <input className="form-control bk-input" placeholder="Purpose (e.g. Working capital)"
                        value={drawdownPurpose} onChange={(e) => setDrawdownPurpose(e.target.value)} />
                    </div>
                    <div className="col-md-3">
                      <button type="submit" className="btn btn-bk-outline w-100">Raise Drawdown</button>
                    </div>
                  </form>
                </div>

                {/* Drawdowns table */}
                {drawdowns.length > 0 && (
                  <table className="table bk-table mb-0">
                    <thead><tr><th>DD #</th><th>Amount</th><th>Purpose</th><th>Status</th><th>Due Date</th><th>Action</th></tr></thead>
                    <tbody>
                      {drawdowns.map((d) => (
                        <React.Fragment key={d.drawdownId}>
                        <tr>
                          <td className="bk-mono">#{d.drawdownId}</td>
                          <td>{formatINR(d.amount)}</td>
                          <td >{d.purpose || '—'}</td>
                          <td>
                            <span className={`badge text-bg-${d.status === 'DISBURSED' ? 'success' : d.status === 'OVERDUE' ? 'danger' : d.status === 'REPAID' ? 'neutral' : 'info'}`}>
                              {d.status}
                            </span>
                          </td>
                          <td >{d.repaymentDate || '—'}</td>
                          <td>
                            {d.status === 'REQUESTED' && confirmDisburseId !== d.drawdownId && (
                              <button className="btn btn-bk-primary btn-sm"
                                onClick={() => setConfirmDisburseId(d.drawdownId)}>
                                <i className="bi bi-bank me-1"></i>Transfer to Account
                              </button>
                            )}
                            {(d.status === 'DISBURSED' || d.status === 'OVERDUE') && repaymentTarget?.drawdownId !== d.drawdownId && (
                              <button className="btn btn-bk-outline btn-sm"
                                onClick={() => setRepaymentTarget(d)}>
                                Record Repayment
                              </button>
                            )}
                          </td>
                        </tr>
                        {d.status === 'REQUESTED' && confirmDisburseId === d.drawdownId && (
                          <tr>
                            <td colSpan={6} style={{ padding: 0 }}>
                              <div className="p-3 m-2" style={{ background: '#fff8e6', border: '1px solid #f0c674', borderRadius: '10px' }}>
                                <div className="d-flex justify-content-between align-items-center flex-wrap gap-3">
                                  <div>
                                    <div className="bk-label mb-1">CONFIRM DISBURSEMENT</div>
                                    <div className="bk-stat-value" style={{ fontSize: '1.6rem', color: 'var(--bk-navy)' }}>
                                      {formatINR(d.amount)}
                                    </div>
                                    <div className="text-muted small mt-1">
                                      To <strong>{businessBankDetails?.beneficiaryName || 'borrower'}</strong>
                                      {businessBankDetails?.beneficiaryAccountNo && (
                                        <> — A/C <span className="bk-mono">{businessBankDetails.beneficiaryAccountNo}</span>
                                          {businessBankDetails?.beneficiaryIfsc && <>, IFSC <span className="bk-mono">{businessBankDetails.beneficiaryIfsc}</span></>}
                                        </>
                                      )}
                                    </div>
                                    <div className="text-muted small">This action cannot be undone.</div>
                                  </div>
                                  <div className="d-flex gap-2">
                                    <button className="btn btn-bk-outline" onClick={() => setConfirmDisburseId(null)}>Cancel</button>
                                    <button className="btn btn-bk-primary"
                                      onClick={() => { handleDisburse(d.drawdownId); setConfirmDisburseId(null); }}>
                                      <i className="bi bi-check2-circle me-1"></i>Confirm & Transfer
                                    </button>
                                  </div>
                                </div>
                              </div>
                            </td>
                          </tr>
                        )}
                        </React.Fragment>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>

              {/* Repayment form */}
              {repaymentTarget && (
                <div className="bk-card p-4 mb-3">
                  <h6 className="bk-label mb-1" >
                    RECORD REPAYMENT — Drawdown #{repaymentTarget.drawdownId} ({formatINR(repaymentTarget.amount)})
                  </h6>
                  <form onSubmit={handleRecordRepayment} className="row g-2 align-items-end">
                    <div className="col-md-4">
                      <label className="bk-label">Amount Received</label>
                      <SmartAmountInput value={repaymentForm.amount} onChange={(e) => setRepaymentForm({ ...repaymentForm, amount: e.target.value })} max={repaymentTarget.amount} />
                    </div>
                    <div className="col-md-3">
                      <label className="bk-label">Method</label>
                      <select className="form-select bk-input" value={repaymentForm.paymentMethod}
                        onChange={(e) => setRepaymentForm({ ...repaymentForm, paymentMethod: e.target.value })}>
                        {PAYMENT_METHODS.map((m) => <option key={m} value={m}>{m}</option>)}
                      </select>
                    </div>
                    <div className="col-md-3">
                      <label className="bk-label">Reference / UTR</label>
                      <input className="form-control bk-input" value={repaymentForm.referenceNumber}
                        onChange={(e) => setRepaymentForm({ ...repaymentForm, referenceNumber: e.target.value })} />
                    </div>
                    <div className="col-md-2 d-flex gap-1">
                      <button type="submit" className="btn btn-bk-primary flex-grow-1">Record</button>
                      <button type="button" className="btn btn-link" onClick={() => setRepaymentTarget(null)}>Cancel</button>
                    </div>
                  </form>
                </div>
              )}

              {/* Repayment history */}
              {repayments.length > 0 && (
                <table className="table bk-table">
                  <thead><tr><th>Amount</th><th>Method</th><th>Reference</th><th>Status</th><th></th></tr></thead>
                  <tbody>
                    {repayments.map((r) => (
                      <tr key={r.repaymentId}>
                        <td>{formatINR(r.amount)}</td>
                        <td>{r.paymentMethod}</td>
                        <td>{r.referenceNumber || '—'}</td>
                        <td>
                          <span className={`badge text-bg-${
                            r.status === 'VERIFIED' ? 'success' :
                            r.status === 'PENDING_VERIFICATION' ? 'warning' :
                            r.status === 'REVERSED' ? 'danger' : 'success'
                          }`}>
                            {r.status === 'PENDING_VERIFICATION' ? 'Awaiting Verification' : r.status}
                          </span>
                        </td>
                        <td>
                          {r.status === 'PENDING_VERIFICATION' && (
                            <button className="btn btn-bk-primary btn-sm"
                              onClick={() => handleVerifyRepayment(r.repaymentId)}>
                              <i className="bi bi-check-circle me-1"></i>Verify
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
