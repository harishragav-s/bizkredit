import SmartAmountInput from '../../components/SmartAmountInput';
import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import smeService from '../../services/smeService';
import creditService from '../../services/creditService';
import { useAuth } from '../../context/AuthContext';
import { nullifyEmptyStrings } from '../../utils/forms';
import { RATIO_BENCHMARKS } from '../../utils/financialReference';
import { formatINR } from '../../utils/currency';

const STATUS_BADGE = { SUBMITTED: 'info', IN_REVIEW: 'warning', UNDERWRITING_APPROVAL: 'warning', SANCTIONED: 'success', REJECTED: 'danger' };
const RISK_BADGE = { LOW: 'success', MEDIUM: 'info', HIGH: 'warning', WATCHLIST: 'danger' };


export default function ProposalBuilder() {
  const { user } = useAuth();

  // All-applications overview
  const [allApplications, setAllApplications] = useState([]);
  const [allStatements, setAllStatements] = useState({});
  const [allProposals, setAllProposals] = useState({});
  const [allDecisions, setAllDecisions] = useState({});
  const [loadingAll, setLoadingAll] = useState(true);

  // Per-application view
  const [selectedAppId, setSelectedAppId] = useState('');
  const [statements, setStatements] = useState([]);
  const [proposals, setProposals] = useState([]);
  const [financialStatementDoc, setFinancialStatementDoc] = useState(null);
  const [businessContext, setBusinessContext] = useState(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [proposalForm, setProposalForm] = useState({ suggestedAmount: '', suggestedRate: '', tenure: '', conditions: '' });

  const loadAll = () => {
    setLoadingAll(true);
    Promise.all([
      smeService.getApplications({ status: 'SUBMITTED' }),
      smeService.getApplications({ status: 'IN_REVIEW' }),
      smeService.getApplications({ status: 'UNDERWRITING_APPROVAL' }),
      smeService.getApplications({ status: 'SANCTIONED' }),
      smeService.getApplications({ status: 'REJECTED' }),
    ]).then(async ([sub, inR, uw, sanc, rej]) => {
      const all = [...sub.data.data, ...inR.data.data, ...uw.data.data, ...sanc.data.data, ...rej.data.data];
      setAllApplications(all);
      const stmtCounts = {};
      const propCounts = {};
      const decMap = {};
      await Promise.all(all.map(async (app) => {
        try {
          const [sRes, pRes] = await Promise.all([
            creditService.getStatements(app.applicationId),
            creditService.getProposalsByApplication(app.applicationId),
          ]);
          stmtCounts[app.applicationId] = sRes.data.data || [];
          propCounts[app.applicationId] = pRes.data.data || [];

          // Look up the decision (if any) for every proposal, so the
          // analyst can track what actually happened to what they
          // proposed - previously only visible one application at a
          // time, by re-opening each one individually.
          await Promise.all((propCounts[app.applicationId] || []).map(async (p) => {
            try {
              const dRes = await creditService.getDecisionByProposal(p.proposalId);
              decMap[p.proposalId] = dRes.data.data;
            } catch { /* no decision recorded yet - normal, not an error */ }
          }));
        } catch {
          stmtCounts[app.applicationId] = [];
          propCounts[app.applicationId] = [];
        }
      }));
      setAllStatements(stmtCounts);
      setAllProposals(propCounts);
      setAllDecisions(decMap);
      setLoadingAll(false);
    }).catch(() => setLoadingAll(false));
  };

  useEffect(() => { loadAll(); }, []);

  useEffect(() => {
    if (!selectedAppId) {
      setStatements([]); setProposals([]); setFinancialStatementDoc(null);
      setBusinessContext(null);
      return;
    }
    creditService.getStatements(selectedAppId).then((res) => setStatements(res.data.data || [])).catch(() => setStatements([]));
    creditService.getProposalsByApplication(selectedAppId).then((res) => setProposals(res.data.data || [])).catch(() => setProposals([]));

    const app = allApplications.find((a) => String(a.applicationId) === String(selectedAppId));
    if (app?.businessId) {
      smeService.getBusiness(app.businessId).then((res) => setBusinessContext(res.data.data)).catch(() => setBusinessContext(null));
      // Only looked up for the "View Statement" reference button below -
      // promoters/full KYC document list are deliberately not fetched at
      // all here anymore, not just hidden, since this page has no other
      // use for them.
      smeService.getDocumentsByBusiness(app.businessId).then((res) => {
        const docs = res.data.data || [];
        setFinancialStatementDoc(docs.find((d) => d.documentType === 'AUDITED_FINANCIALS' && d.filePath) || null);
      }).catch(() => setFinancialStatementDoc(null));
    }
  }, [selectedAppId, allApplications]);


  const handleSelectApplication = async (appId) => {
    setSelectedAppId(appId);
    setError(''); setSuccess('');
    const app = allApplications.find((a) => String(a.applicationId) === String(appId));
    if (app?.status === 'SUBMITTED') {
      try { await smeService.assignAnalyst(appId, user.userId); loadAll(); } catch { /* silent */ }
    }
  };

  const handleViewDoc = async (doc) => {
    if (!doc) return;
    try {
      const res = await smeService.downloadDocumentById(doc.docId);
      const url = window.URL.createObjectURL(res.data);
      window.open(url, '_blank');
      setTimeout(() => window.URL.revokeObjectURL(url), 10000);
    } catch (err) { setError(err.response?.data?.message || 'Could not open document.'); }
  };

  const handleProposalChange = (field) => (e) => setProposalForm({ ...proposalForm, [field]: e.target.value });

  /**
   * ONE submit for the analyst's entire job on an application: saves the
   * financial year, creates the proposal off the back of it, and sends it
   * to Underwriting - previously three separate button clicks across two
   * separate pages.
   *
   * The three backend calls still happen in sequence (addStatement,
   * createProposal, submitProposal) because they're genuinely three
   * different records; only the analyst-facing step count changed. They
   * run in this order deliberately: the scorecard reads the financial
   * statement when auto-rating the proposal, so the statement must exist
   * before the proposal is created or the rating comes out blank.
   *
   * If a financial year is left blank, the statement step is skipped
   * (an analyst adding a second proposal against already-entered
   * financials shouldn't be forced to retype them).
   */
  const handleCreateAndSubmit = async (e) => {
    e.preventDefault();
    setError(''); setSuccess('');
    setSubmitting(true);
    try {
      const createRes = await creditService.createProposal(selectedAppId, nullifyEmptyStrings(proposalForm));
      const created = createRes.data.data;

      const submitRes = await creditService.submitProposal(selectedAppId, created.proposalId);
      const submitted = submitRes.data.data;

      const pRes = await creditService.getProposalsByApplication(selectedAppId);
      setProposals(pRes.data.data);
      setProposalForm({ suggestedAmount: '', suggestedRate: '', tenure: '', conditions: '' });
      loadAll();

      try {
        await creditService.submitForApproval({
          entityType: 'CreditProposal',
          entityId: created.proposalId,
          action: 'SUBMIT_PROPOSAL',
          requiredCheckerRole: 'UNDERWRITING_MANAGER',
        });
      } catch { /* non-critical - the proposal is still SUBMITTED regardless */ }

      // A downstream application-status advance can fail even though the
      // proposal itself saved fine (e.g. sme-loan-service unreachable) -
      // that comes back as a "WARNING: ..." message and must be shown as a
      // warning, not hidden behind a generic success toast.
      if (submitRes.data.message?.startsWith('WARNING:')) {
        setError(submitRes.data.message.replace(/^WARNING:\s*/, ''));
      } else if (submitted.scorecardAutoComputed && submitted.ratingLabel) {
        setSuccess(`Proposal #${submitted.proposalId} scored ${submitted.computedScore}/100, rated ${submitted.ratingLabel} (${submitted.riskCategory}) — sent to Underwriting.`);
      } else {
        setSuccess(`Proposal #${submitted.proposalId} submitted to Underwriting.`);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Could not create and submit the proposal.');
    } finally {
      setSubmitting(false);
    }
  };

  const selectedApp = allApplications.find((a) => String(a.applicationId) === String(selectedAppId));

  const totalStatements = Object.values(allStatements).reduce((s, arr) => s + arr.length, 0);
  const totalProposals = Object.values(allProposals).reduce((s, arr) => s + arr.length, 0);

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="CREDIT_ANALYST" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Credit Analyst Workbench"
            title="Financial Analysis & Proposal"
            subtitle="Build a credit proposal from the financials already on file, and submit it to Underwriting."
          />

          {/* ── All-applications overview ── */}
          {!selectedAppId && (
            <>
              {!loadingAll && (
                <div className="row g-3 mb-4">
                  <div className="col-md-4">
                    <div className="bk-stat-card">
                      <div className="bk-stat-label">Total Applications</div>
                      <div className="bk-stat-value">{allApplications.length}</div>
                    </div>
                  </div>
                  <div className="col-md-4">
                    <div className="bk-stat-card">
                      <div className="bk-stat-label">Financial Statements Filed</div>
                      <div className="bk-stat-value">{totalStatements}</div>
                    </div>
                  </div>
                  <div className="col-md-4">
                    <div className="bk-stat-card">
                      <div className="bk-stat-label">Proposals Submitted</div>
                      <div className="bk-stat-value">{totalProposals}</div>
                    </div>
                  </div>
                </div>
              )}

              {loadingAll && <p className="text-muted">Loading applications...</p>}
              {!loadingAll && allApplications.length === 0 && (
                <div className="bk-empty"><i className="bi bi-inbox"></i>No applications in the system yet.</div>
              )}
              {!loadingAll && allApplications.length > 0 && (
                <table className="table bk-table">
                  <thead>
                    <tr><th>App #</th><th>Product</th><th>Amount</th><th>Status</th><th>Statements</th><th>Proposals</th><th></th></tr>
                  </thead>
                  <tbody>
                    {allApplications.map((app) => {
                      const stmts = allStatements[app.applicationId] || [];
                      const props = allProposals[app.applicationId] || [];
                      return (
                        <tr key={app.applicationId}>
                          <td className="bk-mono">#{app.applicationId}</td>
                          <td>{app.productType?.replaceAll('_', ' ')}</td>
                          <td>{app.requestedAmount != null ? formatINR(app.requestedAmount) : '—'}</td>
                          <td><span className={`badge text-bg-${STATUS_BADGE[app.status] || 'neutral'}`}>{app.status?.replaceAll('_', ' ')}</span></td>
                          <td>{stmts.length === 0 ? <span className="text-muted">None</span> : <span>{stmts.length} FY{stmts.length > 1 ? 's' : ''}</span>}</td>
                          <td>{props.length === 0 ? <span className="text-muted">None</span> : <span>{props.length}</span>}</td>
                          <td>
                            <button className="btn btn-bk-outline btn-sm"
                              onClick={() => handleSelectApplication(app.applicationId)}>
                              {props.length > 0 ? 'Manage' : 'Open'}
                            </button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}

              {/* Cross-application proposal tracker - previously the only
                  way to see what happened to a submitted proposal was to
                  re-open each application one at a time. This flattens
                  every proposal across every application into one list,
                  with its actual outcome, so the analyst can track their
                  own work without hunting through each application. */}
              {!loadingAll && Object.values(allProposals).some((arr) => arr.length > 0) && (
                <div className="mt-4">
                  <h6 className="bk-label mb-2">MY PROPOSAL TRACKER</h6>
                  <table className="table bk-table">
                    <thead>
                      <tr><th>Proposal #</th><th>App #</th><th>Amount</th><th>Score</th><th>Rating</th><th>Proposal Status</th><th>Decision</th></tr>
                    </thead>
                    <tbody>
                      {Object.entries(allProposals).flatMap(([appId, props]) =>
                        props.map((p) => {
                          const decision = allDecisions[p.proposalId];
                          return (
                            <tr key={p.proposalId}>
                              <td className="bk-mono">#{p.proposalId}</td>
                              <td className="bk-mono">#{appId}</td>
                              <td>{formatINR(p.suggestedAmount)}</td>
                              <td className="bk-mono">{p.computedScore != null ? `${p.computedScore}/100` : '—'}</td>
                              <td>{p.ratingLabel || '—'}</td>
                              <td><span className={`badge text-bg-${p.status === 'SUBMITTED' ? 'info' : p.status === 'APPROVED_BY_MANAGER' ? 'success' : 'neutral'}`}>{p.status?.replaceAll('_', ' ')}</span></td>
                              <td>
                                {decision ? (
                                  <span className={`badge text-bg-${decision.status === 'DECLINED' ? 'danger' : decision.status === 'CONDITIONAL_APPROVAL' ? 'warning' : 'success'}`}>
                                    {decision.status?.replaceAll('_', ' ')}
                                  </span>
                                ) : (
                                  <span className="text-muted small">Awaiting decision</span>
                                )}
                              </td>
                            </tr>
                          );
                        })
                      )}
                    </tbody>
                  </table>
                </div>
              )}
            </>
          )}

          {/* ── Per-application view ── */}
          {selectedAppId && (
            <>
              <button className="btn btn-link px-0 mb-3" onClick={() => { setSelectedAppId(''); setError(''); setSuccess(''); }}>
                &larr; Back to all applications
              </button>

              {selectedApp && (
                <div className="d-flex align-items-center gap-3 mb-3">
                  <h5 className="mb-0">
                    Application #{selectedApp.applicationId} — {selectedApp.productType?.replaceAll('_', ' ')}
                  </h5>
                  <span className={`badge text-bg-${STATUS_BADGE[selectedApp.status] || 'neutral'}`}>
                    {selectedApp.status?.replaceAll('_', ' ')}
                  </span>
                  {selectedApp.requestedAmount && (
                    <span className="text-muted small">Requested: {formatINR(selectedApp.requestedAmount)}</span>
                  )}
                </div>
              )}

              {error && <div className="alert alert-danger">{error}</div>}
              {success && <div className="alert alert-success">{success}</div>}

              {/* Business context panel */}
              {businessContext && (
                <div className="bk-card p-3 mb-3">
                  <div className="bk-label mb-1">Business</div>
                  <div className="fw-semibold">{businessContext.businessName}</div>
                  <div className="text-muted mb-2">
                    {businessContext.registrationNumber} · {businessContext.entityType?.replaceAll('_', ' ')} · {businessContext.industry}
                    {businessContext.yearsInOperation != null && ` · ${businessContext.yearsInOperation} yrs`}
                    {' · KYC '}{businessContext.kycStatus}
                  </div>
                  {/* Promoter details and KYC documents are deliberately NOT
                      shown here, same reasoning as Financial Entry - this
                      page's job is proposing loan terms against financials
                      already on file, not reviewing identity documents.
                      KYC verification belongs to Admin's KYC Review page. */}
                </div>
              )}

              {financialStatementDoc ? (
                <div className="bk-card p-3 mb-3 d-flex justify-content-between align-items-center" style={{ maxWidth: '780px', borderLeft: '4px solid var(--bk-gold)' }}>
                  <div>
                    <div className="bk-label">Applicant's Financial Statement (from KYC)</div>
                    <div><i className="bi bi-file-earmark-text me-1"></i>{financialStatementDoc.originalFileName || 'Financial Statement'}</div>
                    <div className="text-muted">Open and enter figures below.</div>
                  </div>
                  <button type="button" className="btn btn-bk-primary" onClick={() => handleViewDoc(financialStatementDoc)}>
                    <i className="bi bi-eye me-1"></i>View Statement
                  </button>
                </div>
              ) : (
                <div className="alert alert-warning py-2 px-3 mb-3" style={{ maxWidth: '780px', fontSize: '0.85rem' }}>
                  <i className="bi bi-info-circle me-1"></i>
                  No Audited Financials uploaded during KYC. Enter figures from whatever source you have.
                </div>
              )}

              {/* Financials are entered on the Financial Entry page - this
                  page only needs them as read-only context to build a
                  proposal against, never a second place to enter them. */}
              {statements.length === 0 ? (
                <div className="alert alert-warning py-2 px-3 mb-3" style={{ maxWidth: '780px' }}>
                  <i className="bi bi-info-circle me-1"></i>
                  No financial statement on file for this application yet — add one on the
                  <a href="/analyst/financials" className="mx-1">Financial Entry</a> page before building a proposal.
                </div>
              ) : (
                <table className="table bk-table mb-4">
                  <thead>
                    <tr><th>FY</th><th>Revenue</th><th>EBITDA</th><th>Net Worth</th><th>Current Ratio</th><th>D/E Ratio</th><th>DSCR</th><th>Status</th></tr>
                  </thead>
                  <tbody>
                    {statements.map((s) => (
                      <tr key={s.statementId}>
                        <td className="bk-mono">{s.financialYear}</td>
                        <td>{s.revenue != null ? formatINR(s.revenue) : '—'}</td>
                        <td>{s.ebitda != null ? formatINR(s.ebitda) : '—'}</td>
                        <td>{s.netWorth != null ? formatINR(s.netWorth) : '—'}</td>
                        <td>{s.currentRatio != null ? <span className={`badge text-bg-${RATIO_BENCHMARKS.currentRatio.evaluate(s.currentRatio)}`}>{s.currentRatio}</span> : '—'}</td>
                        <td>{s.debtEquityRatio != null ? <span className={`badge text-bg-${RATIO_BENCHMARKS.debtEquityRatio.evaluate(s.debtEquityRatio)}`}>{s.debtEquityRatio}</span> : '—'}</td>
                        <td>{s.dscr != null ? <span className={`badge text-bg-${RATIO_BENCHMARKS.dscr.evaluate(s.dscr)}`}>{s.dscr}</span> : '—'}</td>
                        <td><span className={`badge text-bg-${s.status === 'Verified' ? 'success' : 'neutral'}`}>{s.status}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}

              <form onSubmit={handleCreateAndSubmit}>
              {/* Proposal section */}
              <div className="bk-card p-4 row g-3 mb-4">
                <h6 className="bk-label col-12 mb-0">CREDIT PROPOSAL</h6>
                {selectedApp?.requestedAmount != null && (
                  <div className="col-12">
                    <div className="alert alert-info py-2 px-3 mb-0 d-flex align-items-center gap-2">
                      <i className="bi bi-person-badge"></i>
                      <span>Applicant requested: <strong>{formatINR(selectedApp.requestedAmount)}</strong> — your suggested amount can be equal to, or less than, this.</span>
                    </div>
                  </div>
                )}
                <div className="col-md-4">
                  <label className="bk-label">Suggested Amount</label>
                  <SmartAmountInput required value={proposalForm.suggestedAmount} onChange={handleProposalChange('suggestedAmount')} />
                </div>
                <div className="col-md-4">
                  <label className="bk-label">Suggested Rate (%)</label>
                  <input type="number" step="0.01" className="form-control bk-input" required value={proposalForm.suggestedRate} onChange={handleProposalChange('suggestedRate')} />
                </div>
                <div className="col-md-4">
                  <label className="bk-label">Tenure (months)</label>
                  <input type="number" className="form-control bk-input" required value={proposalForm.tenure} onChange={handleProposalChange('tenure')} />
                </div>
                <div className="col-12">
                  <label className="bk-label">Conditions (optional)</label>
                  <textarea className="form-control bk-input" rows={2} value={proposalForm.conditions} onChange={handleProposalChange('conditions')} />
                </div>
                <div className="col-12">
                  <button type="submit" className="btn btn-bk-primary" disabled={submitting}>
                    <i className="bi bi-send-check me-1"></i>
                    {submitting ? 'Submitting…' : 'Submit to Underwriting'}
                  </button>
                </div>
              </div>
              </form>

              <h6 className="bk-label mb-2">PROPOSAL HISTORY</h6>
              {proposals.length === 0 ? (
                <div className="bk-empty"><i className="bi bi-file-earmark-text"></i>No proposals yet — submit one above.</div>
              ) : (
                <table className="table bk-table">
                  <thead>
                    <tr><th>ID</th><th>Amount</th><th>Rate</th><th>Tenure</th><th>Score</th><th>Rating</th><th>Risk</th><th>Status</th></tr>
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
                        <td><span className={`badge text-bg-${p.status === 'SUBMITTED' ? 'info' : p.status === 'APPROVED_BY_MANAGER' ? 'success' : 'neutral'}`}>{p.status?.replaceAll('_', ' ')}</span></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
