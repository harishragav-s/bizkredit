import SmartAmountInput from '../../components/SmartAmountInput';
import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import smeService from '../../services/smeService';
import collateralService from '../../services/collateralService';
import creditService from '../../services/creditService';
import { formatINR } from '../../utils/currency';
import { useAuth } from '../../context/AuthContext';

// Must match collateral-service's AssetType enum exactly - VEHICLE/OTHER
// were never valid values there and would fail on submit.
const ASSET_TYPES = ['PROPERTY', 'PLANT', 'MACHINERY', 'RECEIVABLES', 'GOLD', 'SECURITIES', 'FD'];

export default function CollateralRegister() {
  const { user } = useAuth();

  // Overview
  const [allApplications, setAllApplications] = useState([]);
  const [allCollaterals, setAllCollaterals] = useState({});
  const [businessMap, setBusinessMap] = useState({});
  const [loadingAll, setLoadingAll] = useState(true);

  // Per-application
  const [selectedAppId, setSelectedAppId] = useState('');
  const [collaterals, setCollaterals] = useState([]);
  const [financialStatements, setFinancialStatements] = useState([]);
  const [proposals, setProposals] = useState([]);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [form, setForm] = useState({
    assetType: 'PROPERTY', description: '', ownerName: '', marketValue: '', forceValuePercent: '70',
  });
  const [revaluingId, setRevaluingId] = useState(null);
  const [newMarketValue, setNewMarketValue] = useState('');

  const loadAll = async () => {
    setLoadingAll(true);
    try {
      const res = await smeService.getApplications();
      const apps = res.data.data || [];
      setAllApplications(apps);
      const colMap = {}, bizMap = {};
      await Promise.all(apps.map(async (app) => {
        try {
          const cRes = await collateralService.getCollateralsByApplication(app.applicationId);
          colMap[app.applicationId] = cRes.data.data || [];
        } catch { colMap[app.applicationId] = []; }
        if (app.businessId && !bizMap[app.businessId]) {
          try {
            const bRes = await smeService.getBusiness(app.businessId);
            bizMap[app.businessId] = bRes.data.data.businessName;
          } catch { bizMap[app.businessId] = `Business #${app.businessId}`; }
        }
      }));
      setAllCollaterals(colMap);
      setBusinessMap(bizMap);
    } catch { /* silent */ }
    finally { setLoadingAll(false); }
  };

  useEffect(() => { loadAll(); }, []);

  const loadCollaterals = (appId) => {
    collateralService.getCollateralsByApplication(appId)
      .then((res) => {
        const items = res.data.data || [];
        setCollaterals(items);

        const prefilled = {};
        items.filter((c) => c.status === 'DISCLOSED').forEach((c) => {
          if (c.marketValue != null) prefilled[c.collateralId] = String(c.marketValue);
        });
        setEvaluationValues((prev) => ({ ...prefilled, ...prev }));
      })
      .catch((err) => setError(err.response?.data?.message || 'Could not load collateral.'));
  };

  const handleSelectApp = (appId) => {
    setSelectedAppId(appId);
    setError(''); setSuccess(''); setRevaluingId(null);
    if (appId) {
      loadCollaterals(appId);
      // Load analyst's financial statements for reference
      creditService.getStatements(appId)
        .then((res) => setFinancialStatements(res.data.data || []))
        .catch(() => setFinancialStatements([]));
      // Load credit proposals for reference
      creditService.getProposalsByApplication(appId)
        .then((res) => setProposals(res.data.data || []))
        .catch(() => setProposals([]));
    } else {
      setCollaterals([]);
      setFinancialStatements([]); setProposals([]);
    }
  };

  const handleChange = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  // Pre-fill form from applicant's disclosure
  const [evaluationValues, setEvaluationValues] = useState({});

  const handleEvaluate = async (collateralId) => {
    const confirmed = evaluationValues[collateralId];
    if (!confirmed) { setError('Enter the confirmed value before evaluating.'); return; }
    setError(''); setSuccess('');
    try {
      await collateralService.evaluateCollateral(selectedAppId, collateralId, confirmed);
      setSuccess('Collateral evaluated and confirmed — now counted toward coverage.');
      loadCollaterals(selectedAppId);
      loadAll();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not evaluate collateral.');
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(''); setSuccess('');
    try {
      const res = await collateralService.registerCollateral(selectedAppId, form);
      setSuccess(`Collateral #${res.data.data.collateralId} registered. Realisable value: ${formatINR(res.data.data.realisableValue)}.`);
      loadCollaterals(selectedAppId);
      loadAll();
      setForm({ assetType: 'PROPERTY', description: '', ownerName: '', marketValue: '', forceValuePercent: '70' });
    } catch (err) {
      setError(err.response?.data?.message || 'Could not register collateral.');
    }
  };

  const handleRevalue = async (collateralId) => {
    setError(''); setSuccess('');
    try {
      await collateralService.revalueCollateral(selectedAppId, collateralId, newMarketValue, user.userId);
      setSuccess(`Collateral #${collateralId} revalued to market value ${formatINR(newMarketValue)}.`);
      setNewMarketValue(''); setRevaluingId(null);
      loadCollaterals(selectedAppId);
      loadAll();
    } catch (err) {
      setError(err.response?.data?.message || 'Could not revalue collateral.');
    }
  };

  const selectedApp = allApplications.find((a) => String(a.applicationId) === String(selectedAppId));
  const totalCollaterals = Object.values(allCollaterals).reduce((s, arr) => s + arr.length, 0);
  const totalRealisable = Object.values(allCollaterals).reduce(
    (s, arr) => s + arr.reduce((ss, c) => ss + (Number(c.realisableValue) || 0), 0), 0
  );

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="COLLATERAL_EVALUATOR" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Collateral Evaluator"
            title="Collateral Register"
            subtitle="View what applicants disclosed, then perform formal evaluation and register realisable value. Realisable = Market Value × Force Value %."
          />

          {/* Overview */}
          {!selectedAppId && (
            <>
              {!loadingAll && (
                <div className="row g-3 mb-4" >
                  <div className="col-md-4">
                    <div className="bk-stat-card">
                      <div className="bk-stat-label">Total Items Registered</div>
                      <div className="bk-stat-value">{totalCollaterals}</div>
                    </div>
                  </div>
                  <div className="col-md-4">
                    <div className="bk-stat-card">
                      <div className="bk-stat-label">Total Realisable</div>
                      <div className="bk-stat-value" >{formatINR(totalRealisable)}</div>
                    </div>
                  </div>
                  <div className="col-md-4">
                    <div className="bk-stat-card">
                      <div className="bk-stat-label">Applications</div>
                      <div className="bk-stat-value">{allApplications.length}</div>
                    </div>
                  </div>
                </div>
              )}

              {loadingAll && <p className="text-muted">Loading...</p>}
              {!loadingAll && allApplications.length === 0 && (
                <div className="bk-empty"><i className="bi bi-house-gear"></i>No applications yet.</div>
              )}
              {!loadingAll && allApplications.length > 0 && (
                <table className="table bk-table" >
                  <thead>
                    <tr><th>App #</th><th>Business</th><th>Product</th><th>Status</th><th>Applicant Disclosed</th><th>Formally Registered</th><th>Total Realisable</th><th></th></tr>
                  </thead>
                  <tbody>
                    {allApplications.map((app) => {
                      const cols = allCollaterals[app.applicationId] || [];
                      const disclosed = cols.filter((c) => c.status === 'DISCLOSED');
                      const registered = cols.filter((c) => c.status !== 'DISCLOSED');
                      const totalReal = registered.reduce((s, c) => s + (Number(c.realisableValue) || 0), 0);
                      return (
                        <tr key={app.applicationId}>
                          <td className="bk-mono">#{app.applicationId}</td>
                          <td>{businessMap[app.businessId] || (app.businessId ? `Business #${app.businessId}` : '—')}</td>
                          <td className="small text-muted">{app.productType?.replaceAll('_', ' ')}</td>
                          <td><span className={`badge text-bg-${app.status === 'SANCTIONED' ? 'success' : app.status === 'REJECTED' ? 'danger' : 'neutral'}`}>{app.status?.replaceAll('_', ' ')}</span></td>
                          <td>
                            {disclosed.length > 0
                              ? <span style={{ fontWeight: 600, color: '#b45309' }}>{disclosed.length} awaiting evaluation</span>
                              : <span className="text-muted" >None</span>
                            }
                          </td>
                          <td>
                            {registered.length === 0
                              ? <span className="text-muted" >None registered</span>
                              : <span >{registered.length} item{registered.length > 1 ? 's' : ''}</span>
                            }
                          </td>
                          <td>{totalReal > 0 ? formatINR(totalReal) : '—'}</td>
                          <td>
                            <button className={`btn ${disclosed.length > 0 ? 'btn-bk-primary' : 'btn-bk-outline'}`} style={{ padding: '0.25rem 0.7rem', fontSize: '0.78rem' }}
                              onClick={() => handleSelectApp(app.applicationId)}>
                              {disclosed.length > 0 ? 'Evaluate →' : registered.length > 0 ? 'View / Revalue' : 'Register'}
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

          {/* Per-application */}
          {selectedAppId && (
            <>
              <button className="btn btn-link px-0 mb-3" onClick={() => handleSelectApp('')}>← Back to all applications</button>

              {selectedApp && (
                <div className="d-flex align-items-center gap-3 mb-3">
                  <h5 className="mb-0" >
                    Application #{selectedApp.applicationId} — {selectedApp.productType?.replaceAll('_', ' ')}
                  </h5>
                  {selectedApp.businessId && businessMap[selectedApp.businessId] && (
                    <span className="text-muted" >{businessMap[selectedApp.businessId]}</span>
                  )}
                </div>
              )}

              {error && <div className="alert alert-danger" >{error}</div>}
              {success && <div className="alert alert-success" >{success}</div>}

              {/* Financial statements from analyst — evaluator needs this context */}
              {financialStatements.length > 0 && (
                <div className="bk-card p-4 mb-4" style={{ maxWidth: '800px', borderLeft: '4px solid var(--bk-navy)' }}>
                  <div className="bk-label mb-2" >
                    FINANCIAL STATEMENTS — Entered by Credit Analyst (for context)
                  </div>
                  <table className="table bk-table mb-0" >
                    <thead>
                      <tr><th>FY</th><th>Revenue</th><th>EBITDA</th><th>Total Assets</th><th>Total Liabilities</th><th>Net Worth</th></tr>
                    </thead>
                    <tbody>
                      {financialStatements.map((s) => (
                        <tr key={s.statementId}>
                          <td className="bk-mono">{s.financialYear}</td>
                          <td>{s.revenue != null ? formatINR(s.revenue) : '—'}</td>
                          <td>{s.ebitda != null ? formatINR(s.ebitda) : '—'}</td>
                          <td>{s.totalAssets != null ? formatINR(s.totalAssets) : '—'}</td>
                          <td>{s.totalLiabilities != null ? formatINR(s.totalLiabilities) : '—'}</td>
                          <td><strong>{s.netWorth != null ? formatINR(s.netWorth) : '—'}</strong></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {/* Credit proposals from analyst */}
              {proposals.length > 0 && (
                <div className="bk-card p-3 mb-4" style={{ maxWidth: '800px', borderLeft: '4px solid var(--bk-teal, #0d9488)' }}>
                  <div className="bk-label mb-2" >
                    CREDIT PROPOSALS — For reference when assessing collateral coverage
                  </div>
                  <div className="d-flex flex-wrap gap-3">
                    {proposals.map((p) => (
                      <div key={p.proposalId} style={{ background: 'var(--bk-paper)', borderRadius: '8px', padding: '8px 12px', minWidth: '180px' }}>
                        <div className="bk-label" >Proposal #{p.proposalId}</div>
                        <div><strong>{formatINR(p.suggestedAmount)}</strong> @ {p.suggestedRate}%</div>
                        <div className="text-muted">{p.tenure ? `${p.tenure} months` : '—'}</div>
                        {p.computedScore != null && (
                          <div>Score: <span className="bk-mono">{p.computedScore}/100</span></div>
                        )}
                        <span className={`badge text-bg-${p.status === 'SUBMITTED' ? 'info' : p.status === 'APPROVED' ? 'success' : 'neutral'}`} >
                          {p.status?.replaceAll('_', ' ')}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Applicant-disclosed items awaiting evaluation */}
              {collaterals.some((c) => c.status === 'DISCLOSED') && (
                <div className="bk-card p-4 mb-4" style={{ maxWidth: '800px', borderLeft: '4px solid var(--bk-gold)' }}>
                  <div className="bk-label mb-2" >
                    APPLICANT-DISCLOSED — CONFIRM THE REAL VALUE
                  </div>
                  <div className="form-text mb-2">
                    Enter your assessed market value and click Confirm — this updates
                    the applicant's existing disclosure in place. Do not re-enter these
                    in the "Register Collateral Directly" form below, which is only for
                    assets the applicant never disclosed (that would create a duplicate).
                  </div>
                  <table className="table bk-table mb-0">
                    <thead><tr><th>Type</th><th>Description</th><th>Applicant's Estimate</th><th>Confirmed Value</th><th></th></tr></thead>
                    <tbody>
                      {collaterals.filter((c) => c.status === 'DISCLOSED').map((c) => (
                        <tr key={c.collateralId}>
                          <td>{c.assetType?.replaceAll('_', ' ')}</td>
                          <td className="small text-muted">{c.description}</td>
                          <td>{formatINR(c.marketValue)}</td>
                          <td style={{ width: '180px' }}>
                            <input type="number" className="form-control bk-input" placeholder="Confirmed ₹"
                              value={evaluationValues[c.collateralId] ?? ''}
                              onChange={(e) => setEvaluationValues({ ...evaluationValues, [c.collateralId]: e.target.value })} />
                          </td>
                          <td>
                            <button className="btn btn-bk-primary btn-sm"
                              onClick={() => handleEvaluate(c.collateralId)}>
                              Confirm
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {/* Register form */}
              <form onSubmit={handleSubmit} className="bk-card p-4 row g-3 mb-4" >
                <h6 className="bk-label col-12 mb-0" >REGISTER COLLATERAL DIRECTLY</h6>
                <div className="col-md-4">
                  <label className="bk-label">Asset Type</label>
                  <select className="form-select bk-input" value={form.assetType} onChange={handleChange('assetType')}>
                    {ASSET_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
                  </select>
                </div>
                <div className="col-md-8">
                  <label className="bk-label">Description</label>
                  <input className="form-control bk-input" required value={form.description} onChange={handleChange('description')}
                    placeholder="e.g. Residential property at 12 MG Road, Chennai — 1200 sqft" />
                </div>
                <div className="col-md-4">
                  <label className="bk-label">Owner Name</label>
                  <input className="form-control bk-input" required value={form.ownerName} onChange={handleChange('ownerName')} />
                </div>
                <div className="col-md-4">
                  <label className="bk-label">Market Value (Formal Assessment)</label>
                  <SmartAmountInput required value={form.marketValue} onChange={handleChange('marketValue')} />
                </div>
                <div className="col-md-4">
                  <label className="bk-label">Force Value % <span className="text-muted" >(haircut)</span></label>
                  <input type="number" className="form-control bk-input" required min={1} max={100}
                    value={form.forceValuePercent} onChange={handleChange('forceValuePercent')} />
                  <div className="form-text">
                    Realisable = {form.marketValue ? formatINR(form.marketValue) : 'Market Value'} × {form.forceValuePercent || 0}%
                    {form.marketValue && form.forceValuePercent
                      ? ` = ${formatINR(Number(form.marketValue) * Number(form.forceValuePercent) / 100)}`
                      : ''}
                  </div>
                </div>
                <div className="col-12">
                  <button type="submit" className="btn btn-bk-primary">
                    <i className="bi bi-plus-lg me-1"></i>Register Collateral
                  </button>
                </div>
              </form>

              {/* Registered collateral */}
              <h6 className="bk-label mb-2" >REGISTERED COLLATERAL</h6>
              {collaterals.filter((c) => c.status !== 'DISCLOSED').length === 0 ? (
                <div className="bk-empty" >
                  <i className="bi bi-house-gear"></i>
                  {collaterals.some((c) => c.status === 'DISCLOSED')
                    ? 'Applicant has declared assets above — confirm a value to register them formally.'
                    : 'No collateral registered yet. Use the form above.'}
                </div>
              ) : (
                <table className="table bk-table" >
                  <thead><tr><th>ID</th><th>Type</th><th>Description</th><th>Owner</th><th>Market Value</th><th>Force %</th><th>Realisable</th><th></th></tr></thead>
                  <tbody>
                    {collaterals.filter((c) => c.status !== 'DISCLOSED').map((c) => (
                      <React.Fragment key={c.collateralId}>
                        <tr>
                          <td className="bk-mono">#{c.collateralId}</td>
                          <td>{c.assetType}</td>
                          <td className="small text-muted">{c.description}</td>
                          <td className="small text-muted">{c.ownerName || '—'}</td>
                          <td>{formatINR(c.marketValue)}</td>
                          <td>{c.forceValuePercent != null ? `${c.forceValuePercent}%` : '—'}</td>
                          <td><strong>{formatINR(c.realisableValue)}</strong></td>
                          <td>
                            {revaluingId !== c.collateralId
                              ? <button className="btn btn-bk-outline btn-sm"
                                  onClick={() => { setRevaluingId(c.collateralId); setNewMarketValue(''); }}>Revalue</button>
                              : <button className="btn btn-link p-0 text-muted" 
                                  onClick={() => setRevaluingId(null)}>Cancel</button>
                            }
                          </td>
                        </tr>
                        {revaluingId === c.collateralId && (
                          <tr>
                            <td colSpan={8} style={{ background: 'var(--bk-paper)' }}>
                              <div className="d-flex gap-2 align-items-end py-2 px-1">
                                <div style={{ minWidth: '240px' }}>
                                  <label className="bk-label">New Market Value</label>
                                  <SmartAmountInput value={newMarketValue} onChange={(e) => setNewMarketValue(e.target.value)} />
                                </div>
                                {newMarketValue && (
                                  <div className="text-muted" style={{ paddingBottom: '0.5rem' }}>
                                    New realisable: {formatINR(Number(newMarketValue) * (c.forceValuePercent ?? 70) / 100)}
                                  </div>
                                )}
                                <button className="btn btn-bk-primary" onClick={() => handleRevalue(c.collateralId)}>
                                  Submit Revaluation
                                </button>
                              </div>
                            </td>
                          </tr>
                        )}
                      </React.Fragment>
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
