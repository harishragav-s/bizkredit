import SmartAmountInput from '../../components/SmartAmountInput';
import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import smeService from '../../services/smeService';
import creditService from '../../services/creditService';
import { formatINR } from '../../utils/currency';

const DECISION_STATUSES = ['APPROVED', 'DECLINED', 'CONDITIONAL_APPROVAL'];
const RISK_BADGE = { LOW: 'success', MEDIUM: 'info', HIGH: 'warning', WATCHLIST: 'danger' };

export default function DecisionForm() {
  // All-proposals overview
  const [allApplications, setAllApplications] = useState([]);
  const [allProposals, setAllProposals] = useState({});
  const [allDecisions, setAllDecisions] = useState({});
  const [loadingAll, setLoadingAll] = useState(true);

  // Per-application decision flow
  const [selectedAppId, setSelectedAppId] = useState('');
  const [proposals, setProposals] = useState([]);
  const [decidedProposals, setDecidedProposals] = useState([]);
  const [selectedProposal, setSelectedProposal] = useState(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [decision, setDecision] = useState({
    sanctionedAmount: '', approvedRate: '', tenure: '', specialConditions: '', status: 'APPROVED',
  });

  const loadAll = async () => {
    setLoadingAll(true);
    try {
      const results = await Promise.all([
        smeService.getApplications({ status: 'UNDERWRITING_APPROVAL' }),
        smeService.getApplications({ status: 'SANCTIONED' }),
        smeService.getApplications({ status: 'REJECTED' }),
        smeService.getApplications({ status: 'IN_REVIEW' }),
        smeService.getApplications({ status: 'DISBURSED' }),
      ]);
      const all = results.flatMap((r) => r.data.data);
      setAllApplications(all);
      const propMap = {};
      const decMap = {};
      await Promise.all(all.map(async (app) => {
        try {
          const res = await creditService.getProposalsByApplication(app.applicationId);
          propMap[app.applicationId] = res.data.data || [];
          // Get decisions for each proposal
          await Promise.all((propMap[app.applicationId] || []).map(async (p) => {
            try {
              const dRes = await creditService.getDecisionByProposal(p.proposalId);
              decMap[p.proposalId] = dRes.data.data;
            } catch { /* no decision yet */ }
          }));
        } catch { propMap[app.applicationId] = []; }
      }));
      setAllProposals(propMap);
      setAllDecisions(decMap);
    } catch { /* silent */ }
    finally { setLoadingAll(false); }
  };

  useEffect(() => { loadAll(); }, []);

  const loadAppProposals = async (appId) => {
    try {
      const [pendingRes, allRes] = await Promise.all([
        creditService.getProposalsByApplication(appId, 'SUBMITTED'),
        creditService.getProposalsByApplication(appId),
      ]);
      setProposals(pendingRes.data.data || []);
      // All non-pending (already decided or draft)
      const pending = pendingRes.data.data || [];
      const pendingIds = new Set(pending.map((p) => p.proposalId));
      setDecidedProposals((allRes.data.data || []).filter((p) => !pendingIds.has(p.proposalId)));
    } catch { setProposals([]); setDecidedProposals([]); }
  };

  const handleSelectApp = (appId) => {
    setSelectedAppId(appId);
    setSelectedProposal(null);
    setError(''); setSuccess('');
    if (appId) loadAppProposals(appId);
  };

  const selectProposal = (proposal) => {
    setSelectedProposal(proposal);
    setDecision({
      sanctionedAmount: proposal.suggestedAmount || '',
      approvedRate: proposal.suggestedRate || '',
      tenure: proposal.tenure || '',
      specialConditions: '',
      status: 'APPROVED',
    });
    setError(''); setSuccess('');
  };

  const handleChange = (field) => (e) => setDecision({ ...decision, [field]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(''); setSuccess('');
    try {
      const res = await creditService.makeDecision(selectedProposal.proposalId, decision);
      setSelectedProposal(null);
      loadAppProposals(selectedAppId);
      loadAll();
      // The decision itself always saves if we get here - but the backend can
      // still fail to advance the application's own status / send notifications
      // downstream (e.g. sme-loan-service unreachable). That comes back as a
      // "WARNING: ..." message and must be shown, not swallowed behind a
      // generic "recorded!" toast, since it means the application is stuck.
      if (res.data.message?.startsWith('WARNING:')) {
        setError(res.data.message.replace(/^WARNING:\s*/, ''));
      } else {
        setSuccess(`Decision recorded for Proposal #${selectedProposal.proposalId}.`);
      }
    } catch (err) { setError(err.response?.data?.message || 'Could not record decision.'); }
  };

  const selectedApp = allApplications.find((a) => String(a.applicationId) === String(selectedAppId));

  // Stats
  const pendingCount = Object.values(allProposals).reduce(
    (s, props) => s + props.filter((p) => p.status === 'SUBMITTED').length, 0
  );

  // Real decision-history tracking - every decision this manager has ever
  // recorded, tallied by outcome, instead of just a flat table with no
  // summary of what their own track record actually looks like.
  const decisionCounts = Object.values(allDecisions).reduce((acc, d) => {
    if (d?.status) acc[d.status] = (acc[d.status] || 0) + 1;
    return acc;
  }, { APPROVED: 0, DECLINED: 0, CONDITIONAL_APPROVAL: 0 });

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="UNDERWRITING_MANAGER" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Underwriting Manager Console"
            title="Sanction Decisions"
            subtitle="All submitted proposals and past decisions across every application."
          />

          {/* ── All-proposals overview ── */}
          {!selectedAppId && (
            <>
              {!loadingAll && (
                <div className="row g-3 mb-4" >
                  <div className="col-md-3">
                    <div className="bk-stat-card">
                      <div className="bk-stat-label">Awaiting Decision</div>
                      <div className="bk-stat-value" style={{ color: pendingCount > 0 ? 'var(--bk-gold)' : 'inherit' }}>{pendingCount}</div>
                    </div>
                  </div>
                  <div className="col-md-3">
                    <div className="bk-stat-card">
                      <div className="bk-stat-label">Approved (by me)</div>
                      <div className="bk-stat-value" style={{ color: 'var(--bk-green)' }}>{decisionCounts.APPROVED}</div>
                    </div>
                  </div>
                  <div className="col-md-3">
                    <div className="bk-stat-card">
                      <div className="bk-stat-label">Declined (by me)</div>
                      <div className="bk-stat-value" style={{ color: 'var(--bk-danger, #dc2626)' }}>{decisionCounts.DECLINED}</div>
                    </div>
                  </div>
                  <div className="col-md-3">
                    <div className="bk-stat-card">
                      <div className="bk-stat-label">Conditional (by me)</div>
                      <div className="bk-stat-value" style={{ color: 'var(--bk-gold)' }}>{decisionCounts.CONDITIONAL_APPROVAL}</div>
                    </div>
                  </div>
                </div>
              )}

              {loadingAll && <p className="text-muted">Loading...</p>}
              {!loadingAll && allApplications.length === 0 && (
                <div className="bk-empty"><i className="bi bi-shield-check"></i>No applications in scope yet.</div>
              )}
              {!loadingAll && allApplications.length > 0 && (
                <table className="table bk-table" >
                  <thead>
                    <tr><th>App #</th><th>Product</th><th>Requested</th><th>Status</th><th>Proposals</th><th>Decision</th><th></th></tr>
                  </thead>
                  <tbody>
                    {allApplications.map((app) => {
                      const props = allProposals[app.applicationId] || [];
                      const pending = props.filter((p) => p.status === 'SUBMITTED');
                      const latestDecision = props.map((p) => allDecisions[p.proposalId]).filter(Boolean)[0];
                      return (
                        <tr key={app.applicationId}>
                          <td className="bk-mono">#{app.applicationId}</td>
                          <td>{app.productType?.replaceAll('_', ' ')}</td>
                          <td>{app.requestedAmount != null ? formatINR(app.requestedAmount) : '—'}</td>
                          <td>
                            <span className={`badge text-bg-${
                              app.status === 'SANCTIONED' ? 'success' : app.status === 'REJECTED' ? 'danger' :
                              app.status === 'UNDERWRITING_APPROVAL' ? 'warning' : 'neutral'
                            }`}>{app.status?.replaceAll('_', ' ')}</span>
                          </td>
                          <td>
                            {props.length === 0
                              ? <span className="text-muted" >None</span>
                              : <>
                                  <span >{props.length} total</span>
                                  {pending.length > 0 && <span className="ms-1 badge text-bg-warning">{pending.length} pending</span>}
                                </>
                            }
                          </td>
                          <td >
                            {latestDecision
                              ? <span className={`badge text-bg-${latestDecision.status === 'APPROVED' ? 'success' : latestDecision.status === 'DECLINED' ? 'danger' : 'info'}`}>
                                  {latestDecision.status?.replaceAll('_', ' ')}
                                </span>
                              : <span className="text-muted">—</span>
                            }
                          </td>
                          <td>
                            <button className="btn btn-bk-outline btn-sm"
                              onClick={() => handleSelectApp(app.applicationId)}>
                              {pending.length > 0 ? 'Decide' : 'View History'}
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}
            </>
          )}

          {/* ── Per-application view ── */}
          {selectedAppId && (
            <>
              <button className="btn btn-link px-0 mb-3" onClick={() => { handleSelectApp(''); }}>
                &larr; Back to all applications
              </button>

              {selectedApp && (
                <div className="d-flex align-items-center gap-3 mb-3">
                  <h5 className="mb-0" >
                    Application #{selectedApp.applicationId} — {selectedApp.productType?.replaceAll('_', ' ')}
                  </h5>
                  <span className={`badge text-bg-${
                    selectedApp.status === 'SANCTIONED' ? 'success' : selectedApp.status === 'REJECTED' ? 'danger' : 'warning'
                  }`}>{selectedApp.status?.replaceAll('_', ' ')}</span>
                </div>
              )}

              {error && <div className="alert alert-danger" >{error}</div>}
              {success && <div className="alert alert-success" >{success}</div>}

              {/* Pending proposals */}
              {!selectedProposal && (
                <>
                  <h6 className="bk-label mb-2" >PROPOSALS AWAITING DECISION</h6>
                  {proposals.length === 0 ? (
                    <div className="bk-empty" >
                      <i className="bi bi-shield-check"></i>
                      No proposals awaiting decision for this application.
                    </div>
                  ) : (
                    <table className="table bk-table mb-4" >
                      <thead>
                        <tr><th>ID</th><th>Amount</th><th>Rate</th><th>Tenure</th><th>Score</th><th>Rating</th><th>Risk</th><th>Recommendation</th><th></th></tr>
                      </thead>
                      <tbody>
                        {proposals.map((p) => (
                          <tr key={p.proposalId}>
                            <td className="bk-mono">#{p.proposalId}</td>
                            <td>{formatINR(p.suggestedAmount)}</td>
                            <td>{p.suggestedRate}%</td>
                            <td>{p.tenure ? `${p.tenure}m` : '—'}</td>
                            <td className="bk-mono">{p.computedScore != null ? `${p.computedScore}/100` : '—'}</td>
                            <td>{p.ratingLabel || '—'}</td>
                            <td>{p.riskCategory ? <span className={`badge text-bg-${RISK_BADGE[p.riskCategory] || 'neutral'}`}>{p.riskCategory}</span> : '—'}</td>
                            <td >{p.analystRecommendation || '—'}</td>
                            <td>
                              <button className="btn btn-bk-primary" style={{ padding: '0.3rem 0.8rem', fontSize: '0.8rem' }}
                                onClick={() => selectProposal(p)}>
                                Decide
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}

                  {/* Decision history */}
                  {decidedProposals.length > 0 && (
                    <>
                      <h6 className="bk-label mb-2" >PREVIOUS PROPOSALS & DECISIONS</h6>
                      <table className="table bk-table" >
                        <thead>
                          <tr><th>Proposal</th><th>Amount</th><th>Score</th><th>Proposal Status</th><th>Decision</th><th>Sanctioned Amount</th><th>Rate</th><th>Date</th></tr>
                        </thead>
                        <tbody>
                          {decidedProposals.map((p) => {
                            const dec = allDecisions[p.proposalId];
                            return (
                              <tr key={p.proposalId}>
                                <td className="bk-mono">#{p.proposalId}</td>
                                <td>{formatINR(p.suggestedAmount)}</td>
                                <td className="bk-mono">{p.computedScore != null ? `${p.computedScore}/100` : '—'}</td>
                                <td><span className="badge text-bg-neutral">{p.status?.replaceAll('_', ' ')}</span></td>
                                <td>
                                  {dec
                                    ? <span className={`badge text-bg-${dec.status === 'APPROVED' ? 'success' : dec.status === 'DECLINED' ? 'danger' : 'info'}`}>{dec.status?.replaceAll('_', ' ')}</span>
                                    : <span className="text-muted" >No decision</span>
                                  }
                                </td>
                                <td>{dec?.sanctionedAmount ? formatINR(dec.sanctionedAmount) : '—'}</td>
                                <td>{dec?.approvedRate ? `${dec.approvedRate}%` : '—'}</td>
                                <td >{dec?.decisionDate || '—'}</td>
                              </tr>
                            );
                          })}
                        </tbody>
                      </table>
                    </>
                  )}
                </>
              )}

              {/* Decision form */}
              {selectedProposal && (
                <div className="bk-card p-4" >
                  <div className="d-flex justify-content-between align-items-start mb-3">
                    <h5 className="mb-0" >Decision — Proposal #{selectedProposal.proposalId}</h5>
                    <button className="btn btn-link p-0 text-muted"  onClick={() => setSelectedProposal(null)}>
                      Cancel
                    </button>
                  </div>

                  <div className="bk-card p-3 mb-3" style={{ background: 'var(--bk-paper)', fontSize: '0.85rem' }}>
                    <div className="row g-2">
                      <div className="col-6"><span className="bk-label" >Analyst suggested</span><br />{formatINR(selectedProposal.suggestedAmount)} @ {selectedProposal.suggestedRate}%</div>
                      <div className="col-3"><span className="bk-label" >Score</span><br />{selectedProposal.computedScore != null ? `${selectedProposal.computedScore}/100` : '—'}</div>
                      <div className="col-3"><span className="bk-label" >Risk</span><br />{selectedProposal.riskCategory ? <span className={`badge text-bg-${RISK_BADGE[selectedProposal.riskCategory] || 'neutral'}`}>{selectedProposal.riskCategory}</span> : '—'}</div>
                    </div>
                  </div>

                  {error && <div className="alert alert-danger">{error}</div>}
                  {success && <div className="alert alert-success">{success}</div>}

                  <form onSubmit={handleSubmit}>
                    <div className="row g-3">
                      <div className="col-12">
                        <label className="bk-label">Sanctioned Amount</label>
                        <SmartAmountInput min={0} max={selectedProposal.suggestedAmount}
                          value={decision.sanctionedAmount} onChange={handleChange('sanctionedAmount')} />
                      </div>
                      <div className="col-md-6">
                        <label className="bk-label">Approved Rate (%)</label>
                        <input type="number" step="0.01" className="form-control bk-input" required
                          value={decision.approvedRate} onChange={handleChange('approvedRate')} />
                      </div>
                      <div className="col-md-6">
                        <label className="bk-label">Tenure (months)</label>
                        <input type="number" className="form-control bk-input" required
                          value={decision.tenure} onChange={handleChange('tenure')} />
                      </div>
                      <div className="col-12">
                        <label className="bk-label">Decision</label>
                        <div className="d-flex gap-2">
                          {DECISION_STATUSES.map((s) => {
                            const isSelected = decision.status === s;
                            const meta = {
                              APPROVED: { icon: 'bi-check-circle-fill', label: 'Approve', selectedClass: 'btn-bk-primary' },
                              DECLINED: { icon: 'bi-x-circle-fill', label: 'Decline', selectedClass: 'btn-bk-danger' },
                              CONDITIONAL_APPROVAL: { icon: 'bi-exclamation-triangle-fill', label: 'Conditional', selectedClass: 'btn-bk-warning' },
                            }[s];
                            return (
                              <button key={s} type="button"
                                className={`btn flex-grow-1 ${isSelected ? meta.selectedClass : 'btn-bk-choice'}`}
                                onClick={() => setDecision({ ...decision, status: s })}>
                                <i className={`bi ${meta.icon}`}></i>
                                {meta.label}
                              </button>
                            );
                          })}
                        </div>
                      </div>
                      <div className="col-12">
                        <label className="bk-label">Special Conditions / Remarks</label>
                        <textarea className="form-control bk-input" rows={2}
                          value={decision.specialConditions} onChange={handleChange('specialConditions')} />
                      </div>
                      <div className="col-12">
                        <button type="submit" className={`btn ${
                          decision.status === 'DECLINED' ? 'btn-bk-danger' :
                          decision.status === 'CONDITIONAL_APPROVAL' ? 'btn-bk-warning' : 'btn-bk-primary'
                        }`}>
                          <i className={`bi ${
                            decision.status === 'DECLINED' ? 'bi-x-circle-fill' :
                            decision.status === 'CONDITIONAL_APPROVAL' ? 'bi-exclamation-triangle-fill' : 'bi-check-circle-fill'
                          } me-1`}></i>
                          Record Decision
                        </button>
                      </div>
                    </div>
                  </form>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
