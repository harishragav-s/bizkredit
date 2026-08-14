import SmartAmountInput from '../../components/SmartAmountInput';
import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import smeService from '../../services/smeService';
import creditService from '../../services/creditService';
import { useAuth } from '../../context/AuthContext';
import { nullifyEmptyStrings } from '../../utils/forms';
import { RATIO_BENCHMARKS, EBITDA_MARGIN_BENCHMARK } from '../../utils/financialReference';
import { formatINR } from '../../utils/currency';

const STATUS_BADGE = { SUBMITTED: 'info', IN_REVIEW: 'warning', UNDERWRITING_APPROVAL: 'warning', SANCTIONED: 'success', REJECTED: 'danger' };
const RISK_BADGE = { LOW: 'success', MEDIUM: 'info', HIGH: 'warning', WATCHLIST: 'danger' };

export default function FinancialEntry() {
  const { user } = useAuth();

  // All-applications overview
  const [allApplications, setAllApplications] = useState([]);
  const [allStatements, setAllStatements] = useState({});
  const [allProposals, setAllProposals] = useState({});
  const [loadingAll, setLoadingAll] = useState(true);

  // Per-application view
  const [selectedAppId, setSelectedAppId] = useState('');
  const [statements, setStatements] = useState([]);
  const [proposals, setProposals] = useState([]);
  const [financialStatementDoc, setFinancialStatementDoc] = useState(null);
  const [businessContext, setBusinessContext] = useState(null);
  const [kycDocuments, setKycDocuments] = useState([]);
  const [promoters, setPromoters] = useState([]);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const [statementForm, setStatementForm] = useState({
    financialYear: '', revenue: '', ebitda: '', pat: '', totalAssets: '', totalLiabilities: '', netWorth: '',
  });

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
      await Promise.all(all.map(async (app) => {
        try {
          const [sRes, pRes] = await Promise.all([
            creditService.getStatements(app.applicationId),
            creditService.getProposalsByApplication(app.applicationId),
          ]);
          stmtCounts[app.applicationId] = sRes.data.data || [];
          propCounts[app.applicationId] = pRes.data.data || [];
        } catch {
          stmtCounts[app.applicationId] = [];
          propCounts[app.applicationId] = [];
        }
      }));
      setAllStatements(stmtCounts);
      setAllProposals(propCounts);
      setLoadingAll(false);
    }).catch(() => setLoadingAll(false));
  };

  useEffect(() => { loadAll(); }, []);

  useEffect(() => {
    if (!selectedAppId) {
      setStatements([]); setProposals([]); setFinancialStatementDoc(null);
      setBusinessContext(null); setKycDocuments([]); setPromoters([]);
      return;
    }
    creditService.getStatements(selectedAppId).then((res) => setStatements(res.data.data || [])).catch(() => setStatements([]));
    creditService.getProposalsByApplication(selectedAppId).then((res) => setProposals(res.data.data || [])).catch(() => setProposals([]));

    const app = allApplications.find((a) => String(a.applicationId) === String(selectedAppId));
    if (app?.businessId) {
      smeService.getBusiness(app.businessId).then((res) => setBusinessContext(res.data.data)).catch(() => setBusinessContext(null));
      smeService.getPromoters(app.businessId).then((res) => setPromoters(res.data.data || [])).catch(() => setPromoters([]));
      smeService.getDocumentsByBusiness(app.businessId).then((res) => {
        const docs = res.data.data || [];
        setKycDocuments(docs.filter((d) => d.filePath));
        setFinancialStatementDoc(docs.find((d) => d.documentType === 'AUDITED_FINANCIALS' && d.filePath) || null);
      }).catch(() => { setKycDocuments([]); setFinancialStatementDoc(null); });
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

  const handleStatementChange = (field) => (e) => {
    const val = e.target.value;
    const updated = { ...statementForm, [field]: val };
    if (field === 'totalAssets' || field === 'totalLiabilities') {
      const assets = Number(field === 'totalAssets' ? val : statementForm.totalAssets) || 0;
      const liabilities = Number(field === 'totalLiabilities' ? val : statementForm.totalLiabilities) || 0;
      if (assets > 0 || liabilities > 0) updated.netWorth = String(assets - liabilities);
    }
    setStatementForm(updated);
  };


  const handleAddStatement = async (e) => {
    e.preventDefault();
    setError(''); setSuccess('');
    setSubmitting(true);
    try {
      await creditService.addStatement(selectedAppId, nullifyEmptyStrings(statementForm));
      const sRes = await creditService.getStatements(selectedAppId);
      setStatements(sRes.data.data || []);
      setStatementForm({ financialYear: '', revenue: '', ebitda: '', pat: '', totalAssets: '', totalLiabilities: '', netWorth: '' });
      loadAll();
      setSuccess(`FY ${statementForm.financialYear || ''} saved — ratios calculated automatically. The analyst can now build a proposal on the Credit Proposals page.`);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not save the financial statement.');
    } finally {
      setSubmitting(false);
    }
  };

  const liveMargin = statementForm.revenue > 0 && statementForm.ebitda
    ? (Number(statementForm.ebitda) / Number(statementForm.revenue)) * 100 : null;

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
            subtitle="Enter financial statements for review. Ratios are calculated automatically."
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

              {/* Cross-application statement history - the table above is a
                  work queue (what needs doing); this is the record of what
                  has already been filed, which previously required opening
                  each application one at a time to see. */}
              {!loadingAll && Object.values(allStatements).some((arr) => arr.length > 0) && (
                <div className="mt-4">
                  <h6 className="bk-label mb-2">MY FINANCIAL STATEMENT HISTORY</h6>
                  <table className="table bk-table">
                    <thead>
                      <tr><th>App #</th><th>FY</th><th>Revenue</th><th>EBITDA</th><th>Net Worth</th><th>Current Ratio</th><th>DSCR</th><th>Status</th></tr>
                    </thead>
                    <tbody>
                      {Object.entries(allStatements).flatMap(([appId, stmts]) =>
                        stmts.map((s) => (
                          <tr key={s.statementId}>
                            <td className="bk-mono">#{appId}</td>
                            <td className="bk-mono">{s.financialYear}</td>
                            <td>{s.revenue != null ? formatINR(s.revenue) : '—'}</td>
                            <td>{s.ebitda != null ? formatINR(s.ebitda) : '—'}</td>
                            <td>{s.netWorth != null ? formatINR(s.netWorth) : '—'}</td>
                            <td>{s.currentRatio != null ? <span className={`badge text-bg-${RATIO_BENCHMARKS.currentRatio.evaluate(s.currentRatio)}`}>{s.currentRatio}</span> : '—'}</td>
                            <td>{s.dscr != null ? <span className={`badge text-bg-${RATIO_BENCHMARKS.dscr.evaluate(s.dscr)}`}>{s.dscr}</span> : '—'}</td>
                            <td><span className={`badge text-bg-${s.status === 'Verified' ? 'success' : 'neutral'}`}>{s.status}</span></td>
                          </tr>
                        ))
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
                      shown here. This page's job is financial statement entry -
                      the analyst needs the applicant's numbers, not their
                      identity documents. KYC verification is the Admin's
                      responsibility on the KYC Review page; surfacing those
                      documents to every role that merely touches an
                      application spreads personal identity data wider than
                      the workflow actually requires. */}
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

              {/* ONE form, ONE submit: financials + proposal + send to underwriting */}
              <form onSubmit={handleAddStatement}>
                <div className="bk-card p-4 mb-3">
                  <div className="d-flex align-items-center gap-2 mb-1" style={{ borderBottom: '1px solid var(--bk-line)', paddingBottom: '12px' }}>
                    <div className="bk-stat-icon"><i className="bi bi-file-earmark-bar-graph"></i></div>
                    <div>
                      <h6 className="mb-0" style={{ fontWeight: 700, color: 'var(--bk-navy)' }}>Financial Statement</h6>
                      <div className="text-muted small">
                        {statements.length === 0 ? 'Required — used to auto-score the proposal below.' : 'Optional if a year is already on record below.'}
                      </div>
                    </div>
                  </div>

                  <div className="row g-3 mt-1">
                    <div className="col-md-4">
                      <label className="bk-label">Financial Year</label>
                      <input className="form-control bk-input" placeholder="2023-24" value={statementForm.financialYear} onChange={handleStatementChange('financialYear')} />
                    </div>
                  </div>

                  <div className="mt-4 mb-2">
                    <span className="bk-label" style={{ color: 'var(--bk-green)' }}>Profit &amp; Loss</span>
                  </div>
                  <div className="row g-3">
                    <div className="col-md-4">
                      <label className="bk-label">Revenue</label>
                      <SmartAmountInput value={statementForm.revenue} onChange={handleStatementChange('revenue')} />
                    </div>
                    <div className="col-md-4">
                      <label className="bk-label" title="Earnings Before Interest, Tax, Depreciation">EBITDA <i className="bi bi-info-circle text-muted" style={{ fontSize: '0.7rem' }}></i></label>
                      <SmartAmountInput value={statementForm.ebitda} onChange={handleStatementChange('ebitda')} />
                    </div>
                    <div className="col-md-4">
                      <label className="bk-label" title="Profit After Tax">PAT <i className="bi bi-info-circle text-muted" style={{ fontSize: '0.7rem' }}></i></label>
                      <SmartAmountInput value={statementForm.pat} onChange={handleStatementChange('pat')} />
                    </div>
                    {liveMargin !== null && (
                      <div className="col-12">
                        <div className={`alert py-1 px-3 mb-0 small alert-${EBITDA_MARGIN_BENCHMARK.evaluate(liveMargin) === 'success' ? 'success' : EBITDA_MARGIN_BENCHMARK.evaluate(liveMargin) === 'warning' ? 'warning' : 'danger'}`}>
                          EBITDA Margin: <strong>{liveMargin.toFixed(1)}%</strong>
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="mt-4 mb-2">
                    <span className="bk-label" style={{ color: 'var(--bk-green)' }}>Balance Sheet</span>
                  </div>
                  <div className="row g-3">
                    <div className="col-md-4">
                      <label className="bk-label">Total Assets</label>
                      <SmartAmountInput value={statementForm.totalAssets} onChange={handleStatementChange('totalAssets')} />
                    </div>
                    <div className="col-md-4">
                      <label className="bk-label">Total Liabilities</label>
                      <SmartAmountInput value={statementForm.totalLiabilities} onChange={handleStatementChange('totalLiabilities')} />
                    </div>
                    <div className="col-md-4">
                      <label className="bk-label">
                        Net Worth
                        {statementForm.totalAssets && statementForm.totalLiabilities && (
                          <span style={{ marginLeft: '6px', background: 'var(--bk-gold-soft, #fef3c7)', padding: '1px 6px', borderRadius: '8px', fontSize: '0.65rem' }}>AUTO</span>
                        )}
                      </label>
                      <SmartAmountInput value={statementForm.netWorth} onChange={handleStatementChange('netWorth')} />
                    </div>
                  </div>

                  <div className="mt-3">
                    <details>
                      <summary className="text-muted" style={{ fontSize: '0.8rem', cursor: 'pointer' }}>Healthy ratio benchmarks</summary>
                      <div className="mt-2 ps-1">
                        {Object.values(RATIO_BENCHMARKS).map((b) => (
                          <p key={b.label} className="text-muted mb-1 small"><strong>{b.label}:</strong> {b.description}</p>
                        ))}
                      </div>
                    </details>
                  </div>
                </div>

              {statements.length > 0 && (
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

              <div className="col-12">
                <button type="submit" className="btn btn-bk-primary" disabled={submitting}>
                  <i className="bi bi-save2 me-1"></i>
                  {submitting ? 'Saving…' : 'Save Financial Statement'}
                </button>
              </div>
              </form>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
