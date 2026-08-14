import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import smeService from '../../services/smeService';
import { useAuth } from '../../context/AuthContext';
import collateralService from '../../services/collateralService';
import { formatINR } from '../../utils/currency';


export default function MyPortfolio() {
  const { user } = useAuth();
  const [facilities, setFacilities] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const load = async () => {
      setLoading(true);
      setError('');
      try {
        const appsRes = await smeService.getApplications({ applicantUserId: user.userId });
        const businessIds = [...new Set(appsRes.data.data.map((a) => a.businessId).filter(Boolean))];

        if (businessIds.length === 0) {
          setFacilities([]);
          return;
        }

        const results = await Promise.all(
          businessIds.map((id) => collateralService.getFacilitiesByBusiness(id))
        );
        setFacilities(results.flatMap((res) => res.data.data));
      } catch (err) {
        setError(err.response?.data?.message || 'Could not load your portfolio.');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, []);

  const totalSanctioned = facilities.reduce((sum, f) => sum + (Number(f.sanctionedLimit) || 0), 0);
  const totalDisbursed = facilities.reduce((sum, f) => sum + (Number(f.disbursedAmount) || 0), 0);
  const totalOutstanding = facilities.reduce((sum, f) => sum + (Number(f.outstandingBalance) || 0), 0);
  const utilisationPercent = totalSanctioned > 0 ? (totalOutstanding / totalSanctioned) * 100 : 0;

  // Simple proportion bar - outstanding vs available headroom against
  // the total sanctioned amount, no external chart library needed.
  const outstandingPct = totalSanctioned > 0 ? (totalOutstanding / totalSanctioned) * 100 : 0;

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="SME_APPLICANT" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Applicant Portal"
            title="My Portfolio"
            subtitle="Your total borrowing position across all facilities."
          />

          {error && <div className="alert alert-danger">{error}</div>}
          {loading && <p className="text-muted">Loading...</p>}

          {!loading && facilities.length === 0 && (
            <div className="bk-empty">
              <i className="bi bi-pie-chart"></i>
              No facilities yet. Once a facility is created against a sanctioned application, your portfolio appears here.
            </div>
          )}

          {!loading && facilities.length > 0 && (
            <>
              <div className="row g-3 mb-4" >
                <div className="col-md-3">
                  <div className="bk-card p-3">
                    <div className="bk-label mb-1" >Total Sanctioned</div>
                    <div className="fw-semibold">
                      {formatINR(totalSanctioned)}
                    </div>
                  </div>
                </div>
                <div className="col-md-3">
                  <div className="bk-card p-3">
                    <div className="bk-label mb-1" >Total Disbursed</div>
                    <div className="fw-semibold">
                      {formatINR(totalDisbursed)}
                    </div>
                  </div>
                </div>
                <div className="col-md-3">
                  <div className="bk-card p-3">
                    <div className="bk-label mb-1" >Total Outstanding</div>
                    <div className="fw-semibold">
                      {formatINR(totalOutstanding)}
                    </div>
                  </div>
                </div>
                <div className="col-md-3">
                  <div className="bk-card p-3">
                    <div className="bk-label mb-1" >Utilisation</div>
                    <div className="fw-semibold">
                      {utilisationPercent.toFixed(1)}%
                    </div>
                  </div>
                </div>
              </div>

              <div className="bk-card p-4 mb-4" >
                <div className="bk-label mb-2" >Outstanding vs Sanctioned</div>
                <div style={{ background: 'var(--bk-paper)', borderRadius: '6px', height: '28px', overflow: 'hidden', position: 'relative' }}>
                  <div style={{
                    width: `${outstandingPct}%`,
                    background: 'var(--bk-gold)',
                    height: '100%',
                    transition: 'width 0.3s',
                  }} />
                </div>
                <div className="d-flex justify-content-between mt-1">
                  <span className="text-muted small">Outstanding: {formatINR(totalOutstanding)}</span>
                  <span className="text-muted small">Sanctioned: {formatINR(totalSanctioned)}</span>
                </div>
              </div>

              <h6 className="bk-label mb-3" >Facilities</h6>
              <table className="table bk-table" >
                <thead>
                  <tr>
                    <th>Facility</th><th>Sanctioned</th><th>Disbursed</th>
                    <th>Outstanding</th><th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {facilities.map((f) => (
                    <tr key={f.facilityId}>
                      <td>#{f.facilityId}</td>
                      <td>{formatINR(f.sanctionedLimit)}</td>
                      <td>{formatINR(f.disbursedAmount)}</td>
                      <td>{formatINR(f.outstandingBalance)}</td>
                      <td>
                        <span className={`badge text-bg-${f.status === 'ACTIVE' ? 'success' : f.status === 'NPA' ? 'danger' : 'neutral'}`}>
                          {f.status}
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
    </div>
  );
}
