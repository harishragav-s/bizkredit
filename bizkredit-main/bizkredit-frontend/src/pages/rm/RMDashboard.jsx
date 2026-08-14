import React from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import DashboardCard from '../../components/DashboardCard';

export default function RMDashboard() {
  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="RELATIONSHIP_MANAGER" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Relationship Manager Dashboard"
            title="Portfolio & facilities"
            subtitle="Portfolio Overview shows totals. Facilities is where you create and operate individual credit facilities."
          />

          <div className="row g-3">
            <div className="col-md-3">
              <DashboardCard
                to="/rm/portfolio"
                icon="bi-pie-chart"
                title="Portfolio Overview"
                description="Aggregate totals — exposure, asset quality, sector breakdown across all facilities."
              />
            </div>
            <div className="col-md-3">
              <DashboardCard
                to="/rm/facilities"
                icon="bi-bank"
                title="Facilities"
                description="Manage individual facilities — create, transfer funds to borrower, record repayments."
              />
            </div>
            <div className="col-md-3">
              <DashboardCard
                to="/rm/covenants"
                icon="bi-journal-check"
                title="Covenant Tracker"
                description="Financial and non-financial covenants."
              />
            </div>
            <div className="col-md-3">
              <DashboardCard
                to="/rm/ews"
                icon="bi-exclamation-triangle"
                title="EWS Board"
                description="Early warning signals by facility."
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
