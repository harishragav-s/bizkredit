import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import monitoringService from '../../services/monitoringService';
import { formatINR } from '../../utils/currency';

export default function PortfolioSummary() {
  const [summary, setSummary] = useState(null);
  const [assetQuality, setAssetQuality] = useState(null);
  const [sectorExposure, setSectorExposure] = useState([]);
  const [covenantCompliance, setCovenantCompliance] = useState(null);
  const [ewsSignals, setEwsSignals] = useState(null);
  const [renewalPipeline, setRenewalPipeline] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([
      monitoringService.getPortfolioSummary(),
      monitoringService.getAssetQuality(),
      monitoringService.getSectorExposure(),
      monitoringService.getCovenantComplianceSummary(),
      monitoringService.getEwsSignalSummary(),
      monitoringService.getRenewalPipeline(),
    ])
      .then(([s, a, e, cc, ews, rp]) => {
        setSummary(s.data.data);
        setAssetQuality(a.data.data);
        setSectorExposure(e.data.data);
        setCovenantCompliance(cc.data.data);
        setEwsSignals(ews.data.data);
        setRenewalPipeline(rp.data.data);
      })
      .catch((err) => setError(err.response?.data?.message || 'Could not load portfolio data.'));
  }, []);

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="RELATIONSHIP_MANAGER" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Relationship Manager"
            title="Portfolio Overview"
            subtitle="Aggregate read-only view — total exposure, asset quality distribution, and sector breakdown across ALL active facilities. To manage individual facilities (drawdowns, repayments, transfers) go to Facilities."
          />

          {error && <div className="alert alert-danger">{error}</div>}

          {summary && (
            <div className="row g-3 mb-4">
              <div className="col-md-3">
                <div className="bk-stat-card">
                  <div className="bk-stat-label">Total Sanctioned</div>
                  <div className="bk-stat-value">{formatINR(summary.totalSanctionedExposure)}</div>
                </div>
              </div>
              <div className="col-md-3">
                <div className="bk-stat-card">
                  <div className="bk-stat-label">Total Outstanding</div>
                  <div className="bk-stat-value">{formatINR(summary.totalOutstanding)}</div>
                </div>
              </div>
              <div className="col-md-3">
                <div className="bk-stat-card">
                  <div className="bk-stat-label">Active Facilities</div>
                  <div className="bk-stat-value">{summary.activeFacilitiesCount}</div>
                </div>
              </div>
              <div className="col-md-3">
                <div className="bk-stat-card">
                  <div className="bk-stat-label">Utilisation</div>
                  <div className="bk-stat-value">{Number(summary.portfolioUtilisationPercent).toFixed(1)}%</div>
                </div>
              </div>
            </div>
          )}

          <div className="row g-3">
            <div className="col-md-6">
              <h6 className="bk-label mb-3" >Asset Quality Distribution</h6>
              {assetQuality && (
                <table className="table bk-table">
                  <thead><tr><th>Status</th><th>Count</th><th>Outstanding</th></tr></thead>
                  <tbody>
                    {Object.entries(assetQuality)
                      .filter(([key]) => key !== 'totalNPARecords')
                      .map(([status, data]) => (
                        <tr key={status}>
                          <td>{status}</td>
                          <td>{data.count}</td>
                          <td>{formatINR(data.outstanding)}</td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              )}
              {assetQuality && (
                <p className="text-muted small">Total NPA records: {assetQuality.totalNPARecords}</p>
              )}
            </div>

            <div className="col-md-6">
              <h6 className="bk-label mb-3" >Sector-wise Exposure</h6>
              <table className="table bk-table">
                <thead><tr><th>Industry</th><th>Total Outstanding</th></tr></thead>
                <tbody>
                  {sectorExposure.map((row, i) => (
                    <tr key={i}>
                      <td>{row.industry}</td>
                      <td>{formatINR(row.totalOutstanding)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="row g-3 mt-1">
            <div className="col-md-4">
              <h6 className="bk-label mb-3">Covenant Compliance</h6>
              {covenantCompliance && (
                <>
                  <div className="bk-stat-card mb-3">
                    <div className="bk-stat-label">Compliance Rate</div>
                    <div className="bk-stat-value">{Number(covenantCompliance.complianceRatePercent).toFixed(1)}%</div>
                  </div>
                  <p className="small text-muted mb-1">
                    Compliant: {covenantCompliance.compliantCount} · Breached: {covenantCompliance.breachedCount} · Waived: {covenantCompliance.waivedCount}
                  </p>
                  {covenantCompliance.breachedCovenants?.length > 0 && (
                    <table className="table bk-table">
                      <thead><tr><th>Facility</th><th>Description</th></tr></thead>
                      <tbody>
                        {covenantCompliance.breachedCovenants.map((c) => (
                          <tr key={c.covenantId}>
                            <td>#{c.facilityId}</td>
                            <td>{c.description}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </>
              )}
            </div>

            <div className="col-md-4">
              <h6 className="bk-label mb-3">EWS Signal Monitor</h6>
              {ewsSignals && (
                <>
                  <div className="bk-stat-card mb-3">
                    <div className="bk-stat-label">Open Signals</div>
                    <div className="bk-stat-value">{ewsSignals.openCount}</div>
                  </div>
                  <div className="d-flex gap-2 mb-2">
                    {Object.entries(ewsSignals.bySeverity || {}).map(([sev, count]) => (
                      <span
                        key={sev}
                        className={`badge text-bg-${sev === 'RED' ? 'danger' : sev === 'AMBER' ? 'warning' : 'success'}`}
                      >
                        {sev}: {count}
                      </span>
                    ))}
                  </div>
                  <p className="small text-muted mb-0">
                    {Object.entries(ewsSignals.bySignalType || {}).map(([type, count]) => `${type.replaceAll('_', ' ')}: ${count}`).join(' · ')}
                  </p>
                </>
              )}
            </div>

            <div className="col-md-4">
              <h6 className="bk-label mb-3">Renewal Pipeline</h6>
              {renewalPipeline && (
                <>
                  <div className="row g-2 mb-2">
                    <div className="col-4">
                      <div className="bk-stat-card">
                        <div className="bk-stat-label">30d</div>
                        <div className="bk-stat-value">{renewalPipeline.within30Days?.length ?? 0}</div>
                      </div>
                    </div>
                    <div className="col-4">
                      <div className="bk-stat-card">
                        <div className="bk-stat-label">60d</div>
                        <div className="bk-stat-value">{renewalPipeline.within60Days?.length ?? 0}</div>
                      </div>
                    </div>
                    <div className="col-4">
                      <div className="bk-stat-card">
                        <div className="bk-stat-label">90d</div>
                        <div className="bk-stat-value">{renewalPipeline.within90Days?.length ?? 0}</div>
                      </div>
                    </div>
                  </div>
                  <p className="small text-muted mb-0">Facilities expiring soon — see Facility Management to initiate renewal.</p>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
