import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import DashboardCard from '../../components/DashboardCard';
import smeService from '../../services/smeService';
import creditService from '../../services/creditService';


export default function AnalystDashboard() {
  const [counts, setCounts] = useState({ awaitingPickup: 0, inReview: 0, statementsFiled: 0, proposalsSubmitted: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    Promise.all([
      smeService.getApplications({ status: 'SUBMITTED' }),
      smeService.getApplications({ status: 'IN_REVIEW' }),
      smeService.getApplications({ status: 'UNDERWRITING_APPROVAL' }),
      smeService.getApplications({ status: 'SANCTIONED' }),
      smeService.getApplications({ status: 'REJECTED' }),
    ]).then(async ([sub, inR, uw, sanc, rej]) => {
      const all = [...sub.data.data, ...inR.data.data, ...uw.data.data, ...sanc.data.data, ...rej.data.data];
      let statementsFiled = 0;
      let proposalsSubmitted = 0;
      await Promise.all(all.map(async (app) => {
        try {
          const [sRes, pRes] = await Promise.all([
            creditService.getStatements(app.applicationId),
            creditService.getProposalsByApplication(app.applicationId),
          ]);
          statementsFiled += (sRes.data.data || []).length;
          proposalsSubmitted += (pRes.data.data || []).length;
        } catch { /* skip */ }
      }));
      setCounts({
        awaitingPickup: sub.data.data.length,
        inReview: inR.data.data.length + uw.data.data.length,
        statementsFiled,
        proposalsSubmitted,
      });
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="CREDIT_ANALYST" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Credit Analyst Workbench"
            title="Dashboard"
            subtitle="Your workload at a glance, and where each step happens."
          />

          {!loading && (
            <div className="row g-3 mb-4">
              <div className="col-md-3">
                <div className="bk-stat-card">
                  <div className="bk-stat-label">Awaiting Pickup</div>
                  <div className="bk-stat-value" style={{ color: counts.awaitingPickup > 0 ? 'var(--bk-gold)' : 'inherit' }}>{counts.awaitingPickup}</div>
                </div>
              </div>
              <div className="col-md-3">
                <div className="bk-stat-card">
                  <div className="bk-stat-label">In Review / Underwriting</div>
                  <div className="bk-stat-value">{counts.inReview}</div>
                </div>
              </div>
              <div className="col-md-3">
                <div className="bk-stat-card">
                  <div className="bk-stat-label">Statements Filed</div>
                  <div className="bk-stat-value">{counts.statementsFiled}</div>
                </div>
              </div>
              <div className="col-md-3">
                <div className="bk-stat-card">
                  <div className="bk-stat-label">Proposals Submitted</div>
                  <div className="bk-stat-value">{counts.proposalsSubmitted}</div>
                </div>
              </div>
            </div>
          )}

          <div className="row g-3">
            <div className="col-md-6">
              <DashboardCard
                to="/analyst/financials"
                icon="bi-graph-up"
                title="Financial Entry"
                description="View applications, enter financial statements, review computed ratios."
              />
            </div>
            <div className="col-md-6">
              <DashboardCard
                to="/analyst/proposals"
                icon="bi-file-earmark-text"
                title="Credit Proposals"
                description="Build and submit a proposal against financials already on file."
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
