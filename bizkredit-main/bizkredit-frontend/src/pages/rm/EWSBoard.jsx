import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import collateralService from '../../services/collateralService';
import monitoringService from '../../services/monitoringService';
import { formatINR } from '../../utils/currency';

const SEVERITY_BADGE = { GREEN: 'success', AMBER: 'warning', RED: 'danger' };
const SEVERITY_DESC = {
  GREEN: 'Overdue 1–30 days — monitor closely',
  AMBER: 'Overdue 31–60 days — engage borrower immediately',
  RED: 'Overdue 61–90 days — escalate for recovery action',
};

export default function EWSBoard() {
  const [facilities, setFacilities] = useState([]);
  const [facilityId, setFacilityId] = useState('');
  const [signals, setSignals] = useState([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    collateralService
      .getAllFacilities()
      .then((res) => setFacilities(res.data.data || []))
      .catch(() => setFacilities([]));
  }, []);

  useEffect(() => {
    if (!facilityId) { setSignals([]); return; }
    setLoading(true);
    setError('');
    monitoringService
      .getEWS(facilityId)
      .then((res) => setSignals(res.data.data || []))
      .catch((err) => setError(err.response?.data?.message || 'Could not load EWS signals.'))
      .finally(() => setLoading(false));
  }, [facilityId]);

  const selectedFacility = facilities.find((f) => String(f.facilityId) === String(facilityId));
  const hasRed = signals.some((s) => s.severity === 'RED');
  const hasAmber = signals.some((s) => s.severity === 'AMBER');

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="RELATIONSHIP_MANAGER" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Relationship Manager"
            title="Early Warning Signal Board"
            subtitle="EWS signals are generated automatically by the NPA classification engine when a disbursed drawdown goes overdue. Signals escalate from GREEN → AMBER → RED as days-overdue increases."
          />

          <div className="bk-card p-3 mb-4" style={{ maxWidth: '760px', background: 'var(--bk-paper)', fontSize: '0.85rem' }}>
            <div className="row g-2">
              <div className="col-md-4">
                <span className="badge text-bg-success me-2">GREEN</span>
                Overdue 1–30 days. Monitor.
              </div>
              <div className="col-md-4">
                <span className="badge text-bg-warning me-2">AMBER</span>
                Overdue 31–60 days. Engage borrower.
              </div>
              <div className="col-md-4">
                <span className="badge text-bg-danger me-2">RED</span>
                Overdue 61–90 days. Escalate.
              </div>
            </div>
            <div className="mt-2" >
              Drawdowns overdue 90+ days are classified as <strong>NPA</strong> — see Admin → NPA Classification.
            </div>
          </div>

          <div className="mb-4" >
            <label className="bk-label">Select Facility</label>
            {facilities.length === 0 ? (
              <div className="alert alert-info py-2 px-3" >
                No facilities exist yet. Complete the loan flow to create one.
              </div>
            ) : (
              <select
                className="form-select bk-input"
                value={facilityId}
                onChange={(e) => setFacilityId(e.target.value)}
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

          {facilityId && selectedFacility && (
            <div className="d-flex gap-3 mb-3" >
              <span>Sanctioned: <strong>{formatINR(selectedFacility.sanctionedLimit)}</strong></span>
              <span>Outstanding: <strong>{formatINR(selectedFacility.outstandingBalance)}</strong></span>
            </div>
          )}

          {facilityId && (
            <>
              {loading && <p className="text-muted" >Loading signals...</p>}

              {!loading && signals.length === 0 && (
                <div className="bk-empty" >
                  <i className="bi bi-shield-check" ></i>
                  No warning signals for this facility — no overdue drawdowns detected.
                </div>
              )}

              {!loading && signals.length > 0 && (
                <>
                  {hasRed && (
                    <div className="alert alert-danger mb-3" style={{ maxWidth: '760px', fontSize: '0.88rem' }}>
                      <i className="bi bi-exclamation-triangle-fill me-2"></i>
                      <strong>RED signals detected.</strong> Immediate escalation required. Contact the borrower and initiate recovery procedures.
                    </div>
                  )}
                  {!hasRed && hasAmber && (
                    <div className="alert alert-warning mb-3" style={{ maxWidth: '760px', fontSize: '0.88rem' }}>
                      <i className="bi bi-exclamation-triangle me-2"></i>
                      <strong>AMBER signals present.</strong> Contact the borrower to understand the delay and agree a repayment plan.
                    </div>
                  )}

                  <table className="table bk-table" >
                    <thead>
                      <tr><th>Signal Type</th><th>Severity</th><th>What this means</th><th>Detected</th><th>Status</th></tr>
                    </thead>
                    <tbody>
                      {signals.map((s) => (
                        <tr key={s.ewsId}>
                          <td >{s.signalType?.replaceAll('_', ' ')}</td>
                          <td>
                            <span className={`badge text-bg-${SEVERITY_BADGE[s.severity] || 'neutral'}`}>
                              {s.severity}
                            </span>
                          </td>
                          <td >
                            {SEVERITY_DESC[s.severity] || '—'}
                          </td>
                          <td >{s.detectedDate}</td>
                          <td>
                            <span className="badge text-bg-neutral">{s.status}</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}
