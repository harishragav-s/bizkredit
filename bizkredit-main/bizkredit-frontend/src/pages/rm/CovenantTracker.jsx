import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import collateralService from '../../services/collateralService';
import monitoringService from '../../services/monitoringService';
import { formatINR } from '../../utils/currency';
import { nullifyEmptyStrings } from '../../utils/forms';

const COVENANT_TYPES = ['FINANCIAL', 'NON_FINANCIAL'];
const FREQUENCIES = ['MONTHLY', 'QUARTERLY', 'ANNUAL'];

const COVENANT_EXAMPLES = {
  FINANCIAL: [
    'Current Ratio ≥ 1.25',
    'Debt/Equity Ratio ≤ 2.0',
    'DSCR ≥ 1.1',
    'Net Worth ≥ ₹50L',
    'Revenue growth ≥ 10% YoY',
  ],
  NON_FINANCIAL: [
    'No additional secured borrowing without RM approval',
    'Submit audited financials within 90 days of FY end',
    'Maintain insurance on collateral assets',
    'Notify bank of change in promoter shareholding',
    'No dividend payment while loan is outstanding',
  ],
};

export default function CovenantTracker() {
  const [facilities, setFacilities] = useState([]);
  const [facilityId, setFacilityId] = useState('');
  const [covenants, setCovenants] = useState([]);
  const [watchlist, setWatchlist] = useState([]);
  const [checkingOverdue, setCheckingOverdue] = useState(false);

  const handleCheckOverdue = async () => {
    setCheckingOverdue(true);
    setError(''); setSuccess('');
    try {
      const res = await monitoringService.checkOverdueTracking();
      setSuccess(res.data.message);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not run the overdue check.');
    } finally {
      setCheckingOverdue(false);
    }
  };
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [form, setForm] = useState({
    covenantType: 'FINANCIAL',
    description: '',
    thresholdValue: '',
    financialMetric: 'NONE',
    monitoringFrequency: 'QUARTERLY',
  });

  const [expandedCovenantId, setExpandedCovenantId] = useState(null);
  const [trackingHistory, setTrackingHistory] = useState([]);
  const [trackingForm, setTrackingForm] = useState({ period: '', actualValue: '' });
  const [templates, setTemplates] = useState([]);

  useEffect(() => {
    collateralService
      .getAllFacilities()
      .then((res) => setFacilities(res.data.data || []))
      .catch(() => setFacilities([]));

    monitoringService
      .getWatchlist()
      .then((res) => setWatchlist(res.data.data || []))
      .catch(() => setWatchlist([]));

    // Admin-managed covenant template library - seeded with a standard
    // set on first startup so this is never empty for the RM.
    monitoringService
      .getCovenantTemplates()
      .then((res) => setTemplates((res.data.data || []).filter((t) => t.status === 'ACTIVE')))
      .catch(() => setTemplates([]));
  }, []);

  // Pre-fills the covenant form from a template so the RM doesn't retype
  // standard covenants. They can still edit any field before saving -
  // the template is a starting point, not a lock.
  const applyTemplate = (templateId) => {
    if (!templateId) return;
    const t = templates.find((x) => String(x.templateId) === String(templateId));
    if (!t) return;
    setForm({
      covenantType: t.covenantType || 'FINANCIAL',
      description: t.description || '',
      thresholdValue: t.defaultThresholdValue != null ? String(t.defaultThresholdValue) : '',
      monitoringFrequency: t.defaultMonitoringFrequency || 'QUARTERLY',
    });
    setSuccess(`Loaded template "${t.templateName}" — edit any field before adding.`);
  };

  const loadCovenants = (id) => {
    monitoringService
      .getCovenants(id)
      .then((res) => setCovenants(res.data.data || []))
      .catch((err) => setError(err.response?.data?.message || 'Could not load covenants.'));
  };

  useEffect(() => {
    if (facilityId) loadCovenants(facilityId);
    else setCovenants([]);
  }, [facilityId]);

  const handleChange = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const handleCreate = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    try {
      await monitoringService.createCovenant(facilityId, nullifyEmptyStrings(form));
      setSuccess('Covenant added successfully.');
      loadCovenants(facilityId);
      setForm({ covenantType: 'FINANCIAL', description: '', thresholdValue: '', monitoringFrequency: 'QUARTERLY' });
    } catch (err) {
      setError(err.response?.data?.message || 'Could not create covenant.');
    }
  };

  const selectedFacility = facilities.find((f) => String(f.facilityId) === String(facilityId));

  const toggleExpand = (covenant) => {
    if (expandedCovenantId === covenant.covenantId) {
      setExpandedCovenantId(null);
      return;
    }
    setExpandedCovenantId(covenant.covenantId);
    setTrackingForm({ period: '', actualValue: '' });
    monitoringService
      .getCovenantTrackingHistory(covenant.covenantId)
      .then((res) => setTrackingHistory(res.data.data || []))
      .catch(() => setTrackingHistory([]));
  };

  const handleRecordTracking = async (e, covenantId) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    try {
      await monitoringService.recordCovenantTracking(covenantId, nullifyEmptyStrings(trackingForm));
      setSuccess('Compliance recorded for this period.');
      loadCovenants(facilityId);
      const res = await monitoringService.getCovenantTrackingHistory(covenantId);
      setTrackingHistory(res.data.data || []);
      setTrackingForm({ period: '', actualValue: '' });
    } catch (err) {
      setError(err.response?.data?.message || 'Could not record compliance.');
    }
  };

  const handleWaive = async (covenant) => {
    try {
      await monitoringService.waiveCovenant(facilityId, covenant.covenantId);
      loadCovenants(facilityId);
    } catch (err) {
      setError(err.response?.data?.message || 'Could not waive covenant.');
    }
  };

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="RELATIONSHIP_MANAGER" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Relationship Manager"
            title="Covenant Tracker"
            subtitle="Covenants are conditions the borrower must maintain throughout the loan. Financial covenants track ratios; Non-financial covenants track operational obligations."
          />

          <div className="bk-card p-3 mb-4" style={{ maxWidth: '760px', background: 'var(--bk-paper)', fontSize: '0.85rem' }}>
            <strong>What are covenants?</strong> They are loan conditions agreed during sanction.
            Financial covenants (e.g. maintain Current Ratio ≥ 1.25) are checked against statements the analyst enters.
            A covenant moves to <span className="badge text-bg-danger">BREACHED</span> when its condition is violated.
            <br />
            <strong>Tracking is now reminded automatically</strong> — every morning, the system checks
            each covenant's monitoring frequency (monthly/quarterly/annual) against its last recorded
            review, and notifies you if one has gone overdue. You still enter the actual compliance
            figures (that data only you have), but you no longer have to remember the schedule yourself.
          </div>

          <div className="mb-3">
            <button className="btn btn-bk-outline" onClick={handleCheckOverdue} disabled={checkingOverdue}>
              <i className="bi bi-clock-history me-1"></i>
              {checkingOverdue ? 'Checking…' : 'Check Overdue Now'}
            </button>
          </div>

          {watchlist.length > 0 && (
            <div className="bk-card p-3 mb-4" style={{ maxWidth: '760px' }}>
              <h6 className="bk-label mb-2">WATCHLIST — FACILITIES WITH BREACHED COVENANTS</h6>
              <table className="table bk-table mb-0">
                <thead><tr><th>Facility</th><th>Breached Covenants</th><th>Category</th></tr></thead>
                <tbody>
                  {watchlist.map((w) => (
                    <tr key={w.facilityId}>
                      <td className="bk-mono">#{w.facilityId}</td>
                      <td>{w.breachedCovenantCount}</td>
                      <td>
                        <span className={`badge text-bg-${
                          w.watchlistCategory === 'SMA_2' ? 'danger' :
                          w.watchlistCategory === 'SMA_1' ? 'warning' : 'neutral'
                        }`}>
                          {w.watchlistCategory?.replace('_', '-')}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="mb-4" >
            <label className="bk-label">Select Facility</label>
            {facilities.length === 0 ? (
              <div className="alert alert-info py-2 px-3" >
                No active facilities. Complete the loan flow first: Analyst proposal → Underwriting approval → RM creates facility.
              </div>
            ) : (
              <select
                className="form-select bk-input"
                value={facilityId}
                onChange={(e) => { setFacilityId(e.target.value); setError(''); setSuccess(''); }}
              >
                <option value="">-- Select a facility --</option>
                {facilities.map((f) => (
                  <option key={f.facilityId} value={f.facilityId}>
                    #{f.facilityId} — Sanctioned {formatINR(f.sanctionedLimit)} · {f.status}
                  </option>
                ))}
              </select>
            )}
          </div>

          {error && <div className="alert alert-danger" >{error}</div>}
          {success && <div className="alert alert-success" >{success}</div>}

          {facilityId && (
            <>
              {selectedFacility && (
                <div className="d-flex gap-3 mb-4" style={{ maxWidth: '760px', color: '#666' }}>
                  <span>Sanctioned: <strong>{formatINR(selectedFacility.sanctionedLimit)}</strong></span>
                  <span>Outstanding: <strong>{formatINR(selectedFacility.outstandingBalance)}</strong></span>
                  <span>Status: <span className="badge text-bg-success">{selectedFacility.status}</span></span>
                </div>
              )}

              <form onSubmit={handleCreate} >
                <div className="bk-card p-4 mb-4">
                  <h6 className="bk-label mb-3" >ADD COVENANT</h6>

                  {templates.length > 0 && (
                    <div className="mb-3 pb-3 border-bottom">
                      <label className="bk-label">Start From a Template</label>
                      <select
                        className="form-select bk-input"
                        style={{ maxWidth: '480px' }}
                        defaultValue=""
                        onChange={(e) => { applyTemplate(e.target.value); e.target.value = ''; }}
                      >
                        <option value="">— Choose a standard covenant —</option>
                        <optgroup label="Financial">
                          {templates.filter((t) => t.covenantType === 'FINANCIAL').map((t) => (
                            <option key={t.templateId} value={t.templateId}>{t.templateName}</option>
                          ))}
                        </optgroup>
                        <optgroup label="Non-Financial">
                          {templates.filter((t) => t.covenantType === 'NON_FINANCIAL').map((t) => (
                            <option key={t.templateId} value={t.templateId}>{t.templateName}</option>
                          ))}
                        </optgroup>
                      </select>
                      <div className="form-text">
                        Pre-fills the fields below — you can still edit anything before adding.
                      </div>
                    </div>
                  )}

                  <div className="row g-3">
                    <div className="col-md-3">
                      <label className="bk-label">Type</label>
                      <select
                        className="form-select bk-input"
                        value={form.covenantType}
                        onChange={handleChange('covenantType')}
                      >
                        {COVENANT_TYPES.map((t) => (
                          <option key={t} value={t}>{t.replaceAll('_', ' ')}</option>
                        ))}
                      </select>
                      <div className="form-text">
                        {form.covenantType === 'FINANCIAL'
                          ? 'Ratio or amount-based condition (e.g. Current Ratio ≥ 1.25)'
                          : 'Operational obligation (e.g. submit audited accounts)'}
                      </div>
                    </div>
                    <div className="col-md-5">
                      <label className="bk-label">Description</label>
                      <input
                        className="form-control bk-input"
                        required
                        placeholder={COVENANT_EXAMPLES[form.covenantType][0]}
                        value={form.description}
                        onChange={handleChange('description')}
                      />
                      <div className="form-text">
                        Examples: {COVENANT_EXAMPLES[form.covenantType].slice(0, 2).join(' · ')}
                      </div>
                    </div>
                    {form.covenantType === 'FINANCIAL' && (
                      <div className="col-md-2">
                        <label className="bk-label">Auto-Check Against</label>
                        <select
                          className="form-select bk-input"
                          value={form.financialMetric}
                          onChange={handleChange('financialMetric')}
                        >
                          <option value="NONE">Manual only</option>
                          <option value="CURRENT_RATIO">Current Ratio</option>
                          <option value="DEBT_EQUITY_RATIO">Debt-Equity Ratio</option>
                          <option value="DSCR">DSCR</option>
                          <option value="NET_WORTH">Net Worth</option>
                          <option value="EBITDA_MARGIN">EBITDA Margin</option>
                        </select>
                        <div className="form-text">
                          Maps this covenant to a ratio the system can read directly
                          from the applicant's own financial statement, so overdue
                          reviews are evaluated automatically instead of only
                          reminding you to check it yourself.
                        </div>
                      </div>
                    )}
                    <div className="col-md-2">
                      <label className="bk-label">
                        Threshold
                        {form.covenantType === 'FINANCIAL' && (
                          <span className="text-muted" > (ratio/₹)</span>
                        )}
                      </label>
                      <input
                        type="number"
                        step="0.01"
                        className="form-control bk-input"
                        placeholder={form.covenantType === 'FINANCIAL' ? '1.25' : 'Optional'}
                        value={form.thresholdValue}
                        onChange={handleChange('thresholdValue')}
                      />
                      {form.covenantType === 'FINANCIAL' && (
                        <div className="form-text">Enter the ratio value (e.g. 1.25 for Current Ratio ≥ 1.25)</div>
                      )}
                    </div>
                    <div className="col-md-2">
                      <label className="bk-label">Check Frequency</label>
                      <select
                        className="form-select bk-input"
                        value={form.monitoringFrequency}
                        onChange={handleChange('monitoringFrequency')}
                      >
                        {FREQUENCIES.map((f) => <option key={f} value={f}>{f}</option>)}
                      </select>
                    </div>
                    <div className="col-12">
                      <button type="submit" className="btn btn-bk-primary">
                        <i className="bi bi-plus-lg me-1"></i>Add Covenant
                      </button>
                    </div>
                  </div>
                </div>
              </form>

              <h6 className="bk-label mb-3" >COVENANTS ON THIS FACILITY</h6>
              {covenants.length === 0 ? (
                <div className="bk-empty" >
                  <i className="bi bi-journal-check"></i>
                  No covenants for this facility yet. Add the first covenant above.
                </div>
              ) : (
                <table className="table bk-table" >
                  <thead>
                    <tr><th>Type</th><th>Description</th><th>Threshold</th><th>Frequency</th><th>Status</th><th></th></tr>
                  </thead>
                  <tbody>
                    {covenants.map((c) => (
                      <React.Fragment key={c.covenantId}>
                        <tr>
                          <td>
                            <span className="badge text-bg-neutral">{c.covenantType?.replaceAll('_', ' ')}</span>
                          </td>
                          <td >{c.description}</td>
                          <td >{c.thresholdValue ?? '—'}</td>
                          <td className="small text-muted">{c.monitoringFrequency}</td>
                          <td>
                            <span className={`badge text-bg-${
                              c.status === 'BREACHED' ? 'danger' :
                              c.status === 'WAIVED' ? 'neutral' : 'success'
                            }`}>
                              {c.status}
                            </span>
                          </td>
                          <td className="d-flex gap-2">
                            <button className="btn btn-sm btn-bk-outline" onClick={() => toggleExpand(c)}>
                              {expandedCovenantId === c.covenantId ? 'Close' : 'Track'}
                            </button>
                            {c.status !== 'WAIVED' && (
                              <button className="btn btn-sm btn-bk-outline" onClick={() => handleWaive(c)}>
                                Waive
                              </button>
                            )}
                          </td>
                        </tr>
                        {expandedCovenantId === c.covenantId && (
                          <tr>
                            <td colSpan={6}>
                              <div className="bk-card p-3" style={{ background: 'var(--bk-paper)' }}>
                                <form onSubmit={(e) => handleRecordTracking(e, c.covenantId)} className="row g-2 align-items-end mb-3">
                                  <div className="col-md-3">
                                    <label className="bk-label">Period</label>
                                    <input
                                      className="form-control bk-input"
                                      placeholder="e.g. 2025-Q1"
                                      required
                                      value={trackingForm.period}
                                      onChange={(e) => setTrackingForm({ ...trackingForm, period: e.target.value })}
                                    />
                                  </div>
                                  <div className="col-md-3">
                                    <label className="bk-label">Actual Value</label>
                                    <input
                                      type="number"
                                      step="0.01"
                                      className="form-control bk-input"
                                      placeholder={c.covenantType === 'FINANCIAL' ? 'e.g. 1.10' : 'Optional'}
                                      value={trackingForm.actualValue}
                                      onChange={(e) => setTrackingForm({ ...trackingForm, actualValue: e.target.value })}
                                    />
                                  </div>
                                  <div className="col-md-3">
                                    <button type="submit" className="btn btn-bk-primary">Record Compliance</button>
                                  </div>
                                </form>

                                {trackingHistory.length === 0 ? (
                                  <div className="small text-muted">No compliance history recorded yet for this covenant.</div>
                                ) : (
                                  <table className="table bk-table mb-0">
                                    <thead><tr><th>Period</th><th>Actual</th><th>Threshold</th><th>Status</th><th>Reviewed</th></tr></thead>
                                    <tbody>
                                      {trackingHistory.map((t) => (
                                        <tr key={t.trackingId}>
                                          <td>{t.period}</td>
                                          <td>{t.actualValue ?? '—'}</td>
                                          <td>{t.thresholdValue ?? '—'}</td>
                                          <td>
                                            <span className={`badge text-bg-${
                                              t.complianceStatus === 'BREACHED' ? 'danger' :
                                              t.complianceStatus === 'COMPLIANT' ? 'success' : 'neutral'
                                            }`}>
                                              {t.complianceStatus?.replace('_', ' ')}
                                            </span>
                                          </td>
                                          <td className="small text-muted">{t.reviewDate}</td>
                                        </tr>
                                      ))}
                                    </tbody>
                                  </table>
                                )}
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
