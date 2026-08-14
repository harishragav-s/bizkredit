import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import SmartAmountInput from '../../components/SmartAmountInput';
import collateralService from '../../services/collateralService';
import smeService from '../../services/smeService';
import { useAuth } from '../../context/AuthContext';
import { formatINR } from '../../utils/currency';

export default function MyFacility() {
  const { user } = useAuth();
  const [businesses, setBusinesses] = useState([]);
  const [facilities, setFacilities] = useState([]);
  const [drawdownsByFacility, setDrawdownsByFacility] = useState({});
  const [repaymentsByFacility, setRepaymentsByFacility] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  // Drawdown request form state per facility
  const [requestingFacilityId, setRequestingFacilityId] = useState(null);
  const [requestAmount, setRequestAmount] = useState('');
  const [requestPurpose, setRequestPurpose] = useState('');
  const [submittingRequest, setSubmittingRequest] = useState(false);

  // Repay form state per drawdown
  const [repayingDrawdownId, setRepayingDrawdownId] = useState(null);
  const [repayAmount, setRepayAmount] = useState('');
  const [repayMethod, setRepayMethod] = useState('BANK_TRANSFER');
  const [repayReference, setRepayReference] = useState('');
  const [submittingRepay, setSubmittingRepay] = useState(false);

  const load = async () => {
    try {
      const bizRes = await smeService.getMyBusinesses(user.userId);
      const myBiz = bizRes.data.data || [];
      setBusinesses(myBiz);

      const allFacilities = [];
      for (const biz of myBiz) {
        try {
          const facRes = await collateralService.getFacilitiesByBusiness(biz.businessId);
          const facs = (facRes.data.data || []).map((f) => ({ ...f, businessName: biz.businessName }));
          allFacilities.push(...facs);
        } catch { /* no facility yet */ }
      }
      setFacilities(allFacilities);

      const ddMap = {}, rpMap = {};
      for (const fac of allFacilities) {
        try { ddMap[fac.facilityId] = (await collateralService.getDrawdowns(fac.facilityId)).data.data || []; } catch { ddMap[fac.facilityId] = []; }
        try { rpMap[fac.facilityId] = (await collateralService.getRepaymentsByFacility(fac.facilityId)).data.data || []; } catch { rpMap[fac.facilityId] = []; }
      }
      setDrawdownsByFacility(ddMap);
      setRepaymentsByFacility(rpMap);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not load facility data.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, [user.userId]);

  const handleRequestDrawdown = async (facilityId) => {
    if (!requestAmount) { setError('Enter an amount to request.'); return; }
    setSubmittingRequest(true); setError(''); setSuccess('');
    try {
      // Applicant sends a drawdown request — RM will see it as REQUESTED and disburse
      await collateralService.requestDrawdown(facilityId, { amount: requestAmount, purpose: requestPurpose });
      setSuccess(`✓ Drawdown request of ${formatINR(requestAmount)} sent to your Relationship Manager. They will review and transfer funds to your bank account.`);
      setRequestingFacilityId(null);
      setRequestAmount(''); setRequestPurpose('');
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not submit drawdown request.');
    } finally {
      setSubmittingRequest(false);
    }
  };

  const handleRepay = async (drawdownId) => {
    if (!repayAmount) { setError('Enter the amount you paid.'); return; }
    setSubmittingRepay(true); setError(''); setSuccess('');
    try {
      await collateralService.recordRepayment(drawdownId, {
        amount: repayAmount, paymentMethod: repayMethod, referenceNumber: repayReference || undefined,
      });
      setSuccess(`✓ Repayment of ${formatINR(repayAmount)} submitted. Your Relationship Manager will verify it before it's applied to your balance.`);
      setRepayingDrawdownId(null);
      setRepayAmount(''); setRepayReference('');
      load();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not submit repayment.');
    } finally {
      setSubmittingRepay(false);
    }
  };

  if (loading) return (
    <div className="bk-app-shell"><Navbar />
      <div className="bk-body"><Sidebar role="SME_APPLICANT" />
        <div className="bk-content"><p className="text-muted mt-4">Loading your facilities...</p></div>
      </div>
    </div>
  );

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="SME_APPLICANT" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Applicant Portal"
            title="My Facility"
            subtitle="Your active credit facilities and drawdown history. Request a drawdown to withdraw funds — your Relationship Manager will transfer to your bank account."
          />
          {error && <div className="alert alert-danger">{error}</div>}
          {success && <div className="alert alert-success">{success}</div>}
          {facilities.length === 0 && !error && (
            <div className="bk-empty">
              <i className="bi bi-bank"></i>
              No facilities yet. Your Relationship Manager will set one up once your application is sanctioned.
            </div>
          )}
          {facilities.map((fac) => {
            const dds = drawdownsByFacility[fac.facilityId] || [];
            const rps = repaymentsByFacility[fac.facilityId] || [];
            const overdueCount = dds.filter((d) => d.status === 'OVERDUE').length;
            const rawAvailable = Number(fac.sanctionedLimit) - Number(fac.disbursedAmount);
            // Anything under Rs. 100 is a rounding residual, not real
            // headroom - showing "Available to Draw: ₹3" reads as a bug,
            // not a feature, in a banking UI.
            const available = rawAvailable < 100 ? 0 : rawAvailable;
            const isRequesting = requestingFacilityId === fac.facilityId;
            return (
              <div key={fac.facilityId} className="bk-card mb-4" >
                {/* Header */}
                <div className="p-4 pb-3 border-bottom">
                  <div className="d-flex justify-content-between align-items-start">
                    <div>
                      <h5 className="mb-1" >
                        Facility #{fac.facilityId} — {fac.businessName}
                      </h5>
                      <span className="text-muted" >
                        {fac.productType?.replaceAll('_', ' ')}
                        {fac.interestRate ? ` · ${fac.interestRate}% p.a.` : ''}
                        {fac.expiryDate ? ` · Expires ${fac.expiryDate}` : ''}
                      </span>
                    </div>
                    <span className={`badge text-bg-${fac.status === 'ACTIVE' ? 'success' : fac.status === 'NPA' ? 'danger' : 'neutral'}`}>
                      {fac.status}
                    </span>
                  </div>
                </div>

                {/* Balances */}
                <div className="px-4 py-3 row g-3 border-bottom">
                  {[
                    { label: 'SANCTIONED LIMIT', value: fac.sanctionedLimit },
                    { label: 'TOTAL DISBURSED', value: fac.disbursedAmount, color: '#27ae60' },
                    { label: 'OUTSTANDING', value: fac.outstandingBalance, color: fac.outstandingBalance > 0 ? '#e74c3c' : '#27ae60' },
                    { label: 'AVAILABLE TO DRAW', value: available },
                  ].map((stat) => (
                    <div key={stat.label} className="col-md-3">
                      <div className="bk-label" >{stat.label}</div>
                      <div className="bk-stat-value" style={{ fontSize: '1.5rem', color: stat.color || 'var(--bk-navy)' }}>
                        {formatINR(stat.value)}
                      </div>
                    </div>
                  ))}
                </div>

                {overdueCount > 0 && (
                  <div className="mx-4 mt-3 alert alert-danger py-2 px-3 small text-muted">
                    <i className="bi bi-exclamation-triangle-fill me-1"></i>
                    <strong>{overdueCount} overdue drawdown{overdueCount > 1 ? 's' : ''}.</strong> Contact your Relationship Manager immediately to avoid NPA classification.
                  </div>
                )}

                {/* Request Drawdown */}
                <div className="px-4 pt-3 pb-2">
                  <div className="d-flex justify-content-between align-items-center mb-2">
                    <h6 className="bk-label mb-0" >REQUEST A DRAWDOWN</h6>
                    {!isRequesting && available > 0 && (
                      <button className="btn btn-bk-primary" style={{ padding: '0.3rem 0.9rem' }}
                        onClick={() => { setRequestingFacilityId(fac.facilityId); setRequestAmount(''); setRequestPurpose(''); }}>
                        <i className="bi bi-cash-coin me-1"></i>Request Funds
                      </button>
                    )}
                    {available <= 0 && (
                      <span className="text-muted" >No available limit remaining</span>
                    )}
                  </div>

                  {isRequesting && (
                    <div className="bk-card p-3 mb-3" style={{ background: 'var(--bk-paper)', maxWidth: '600px' }}>
                      <p className="text-muted mb-3 small">
                        Enter the amount you need. Your RM will review and transfer the funds to your registered bank account (<strong>{fac.bankDetails?.beneficiaryAccountNo || 'on file'}</strong>).
                      </p>
                      <div className="mb-3">
                        <label className="bk-label">Amount (max {formatINR(available)} available)</label>
                        <SmartAmountInput
                          value={requestAmount}
                          onChange={(e) => setRequestAmount(e.target.value)}
                          max={available}
                          required
                        />
                      </div>
                      <div className="mb-3">
                        <label className="bk-label">Purpose</label>
                        <input className="form-control bk-input" placeholder="e.g. Working capital, Equipment purchase, Raw material procurement"
                          value={requestPurpose} onChange={(e) => setRequestPurpose(e.target.value)} />
                      </div>
                      <div className="d-flex gap-2">
                        <button className="btn btn-bk-primary" disabled={submittingRequest}
                          onClick={() => handleRequestDrawdown(fac.facilityId)}>
                          {submittingRequest ? 'Sending request...' : 'Send Request to RM'}
                        </button>
                        <button className="btn btn-link" onClick={() => setRequestingFacilityId(null)}>Cancel</button>
                      </div>
                    </div>
                  )}

                  {/* Drawdown history */}
                  {dds.length > 0 && (
                    <table className="table bk-table mb-2">
                      <thead><tr><th>DD #</th><th>Amount</th><th>Purpose</th><th>Disbursed</th><th>Due Date</th><th>Status</th><th></th></tr></thead>
                      <tbody>
                        {dds.map((d) => (
                          <React.Fragment key={d.drawdownId}>
                            <tr>
                              <td className="bk-mono">#{d.drawdownId}</td>
                              <td>{formatINR(d.amount)}</td>
                              <td >{d.purpose || '—'}</td>
                              <td >{d.disbursedDate || '—'}</td>
                              <td style={{ color: d.status === 'OVERDUE' ? '#e74c3c' : 'inherit' }}>{d.repaymentDate || '—'}</td>
                              <td>
                                <span className={`badge text-bg-${d.status === 'DISBURSED' ? 'success' : d.status === 'OVERDUE' ? 'danger' : d.status === 'REPAID' ? 'neutral' : d.status === 'REQUESTED' ? 'info' : 'neutral'}`}>
                                  {d.status}
                                </span>
                                {d.status === 'REQUESTED' && (
                                  <div className="mt-1">Awaiting RM transfer</div>
                                )}
                              </td>
                              <td>
                                {(d.status === 'DISBURSED' || d.status === 'OVERDUE') && (
                                  <button className="btn btn-bk-outline btn-sm"
                                    onClick={() => { setRepayingDrawdownId(d.drawdownId); setRepayAmount(''); setRepayReference(''); }}>
                                    <i className="bi bi-cash-stack me-1"></i>Repay
                                  </button>
                                )}
                              </td>
                            </tr>
                            {repayingDrawdownId === d.drawdownId && (
                              <tr>
                                <td colSpan={7}>
                                  <div className="bk-card p-3 mb-2" style={{ background: 'var(--bk-paper)', maxWidth: '640px' }}>
                                    <p className="text-muted mb-3 small">
                                      Record a payment you've already made on drawdown #{d.drawdownId}. Your Relationship Manager
                                      will verify it before it's applied to your outstanding balance.
                                    </p>
                                    <div className="row g-2 mb-2">
                                      <div className="col-md-5">
                                        <label className="bk-label">Amount Paid</label>
                                        <SmartAmountInput value={repayAmount} onChange={(e) => setRepayAmount(e.target.value)} required />
                                      </div>
                                      <div className="col-md-3">
                                        <label className="bk-label">Method</label>
                                        <select className="form-select bk-input" value={repayMethod} onChange={(e) => setRepayMethod(e.target.value)}>
                                          <option value="BANK_TRANSFER">Bank Transfer</option>
                                          <option value="UPI">UPI</option>
                                          <option value="CHEQUE">Cheque</option>
                                          <option value="CASH">Cash</option>
                                        </select>
                                      </div>
                                      <div className="col-md-4">
                                        <label className="bk-label">Reference / UTR</label>
                                        <input className="form-control bk-input" value={repayReference} onChange={(e) => setRepayReference(e.target.value)} />
                                      </div>
                                    </div>
                                    <div className="d-flex gap-2">
                                      <button className="btn btn-bk-primary" disabled={submittingRepay} onClick={() => handleRepay(d.drawdownId)}>
                                        {submittingRepay ? 'Submitting...' : 'Submit for Verification'}
                                      </button>
                                      <button className="btn btn-link" onClick={() => setRepayingDrawdownId(null)}>Cancel</button>
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
                  {dds.length === 0 && <p className="text-muted mb-2 small">No drawdowns yet — request funds above.</p>}

                  {/* Repayment history */}
                  {rps.length > 0 && (
                    <>
                      <h6 className="bk-label mt-3 mb-2" >REPAYMENT HISTORY</h6>
                      <table className="table bk-table">
                        <thead><tr><th>Amount</th><th>Method</th><th>Reference</th><th>Status</th></tr></thead>
                        <tbody>
                          {rps.map((r) => (
                            <tr key={r.repaymentId}>
                              <td>{formatINR(r.amount)}</td>
                              <td>{r.paymentMethod?.replaceAll('_', ' ')}</td>
                              <td >{r.referenceNumber || '—'}</td>
                              <td>
                                <span className={`badge text-bg-${
                                  r.status === 'VERIFIED' ? 'success' :
                                  r.status === 'PENDING_VERIFICATION' ? 'warning' :
                                  r.status === 'REVERSED' ? 'danger' : 'success'
                                }`}>
                                  {r.status === 'PENDING_VERIFICATION' ? 'Awaiting Verification' : r.status}
                                </span>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
