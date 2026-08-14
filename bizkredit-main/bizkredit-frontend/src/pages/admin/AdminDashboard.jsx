import React from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import DashboardCard from '../../components/DashboardCard';

export default function AdminDashboard() {
  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="ADMIN" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Admin Console"
            title="Platform administration"
            subtitle="User management, application review, loan product configuration, KYC review, and NPA classification."
          />

          <div className="row g-3">
            <div className="col-md-3">
              <DashboardCard
                to="/admin/users"
                icon="bi-people"
                title="User Management"
                description="View users, update account status and lock accounts."
              />
            </div>
            <div className="col-md-3">
              <DashboardCard
                to="/admin/users/create"
                icon="bi-person-plus"
                title="Create Staff User"
                description="Create an account for any internal role."
              />
            </div>
            <div className="col-md-3">
              <DashboardCard
                to="/admin/applications"
                icon="bi-file-earmark-text"
                title="Application Management"
                description="View all applications. Delete rejected ones from the database."
              />
            </div>
            <div className="col-md-3">
              <DashboardCard
                to="/admin/kyc"
                icon="bi-shield-check"
                title="KYC Review"
                description="Review business KYC documents and approve or reject."
              />
            </div>
            <div className="col-md-3">
              <DashboardCard
                to="/admin/products"
                icon="bi-box-seam"
                title="Loan Products"
                description="Configure loan products and eligibility limits."
              />
            </div>


            <div className="col-md-3">
              <DashboardCard
                to="/admin/audit-log"
                icon="bi-clock-history"
                title="Audit Trail"
                description="Browse identity & access actions across the platform."
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
