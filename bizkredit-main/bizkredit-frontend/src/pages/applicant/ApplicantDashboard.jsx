import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import DashboardCard from '../../components/DashboardCard';
import smeService from '../../services/smeService';
import { useAuth } from '../../context/AuthContext';

const REQUIRED_KYC_DOCS = ['Certificate of Incorporation', 'Memorandum of Association', 'GST Returns'];

export default function ApplicantDashboard() {
  const { user } = useAuth();
  const [businesses, setBusinesses] = useState([]);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    const load = async () => {
      try {
        const bizRes = await smeService.getMyBusinesses(user.userId);
        setBusinesses(bizRes.data.data);
      } catch {
        // Leave the banner out if this fails
      } finally {
        setLoaded(true);
      }
    };
    load();
  }, [user.userId]);

  const unverified = businesses.filter((b) => b.kycStatus !== 'Verified');
  // KYC gate: at least one business must be Verified to allow loan application
  const hasVerifiedBusiness = businesses.some((b) => b.kycStatus === 'Verified');

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="SME_APPLICANT" kycVerified={hasVerifiedBusiness} />
        <div className="bk-content">
          <PageHeader
            eyebrow="Applicant Portal"
            title="Welcome"
            subtitle="Manage your business loan applications, documents, and sanctioned facility."
          />

          {/* ── KYC Verified banner ── */}
          {loaded && businesses.length > 0 && unverified.length === 0 && (
            <div className="alert alert-success d-flex align-items-center gap-2 py-2 px-3 mb-4">
              <i className="bi bi-shield-check-fill fs-5"></i>
              <span>
                <strong>KYC Verified.</strong> Your business is verified — you can now apply for a loan.
              </span>
            </div>
          )}

          {/* ── KYC pending / rejected banners ── */}
          {loaded && unverified.map((b) => (
            <div
              key={b.businessId}
              className={`bk-card p-4 mb-3 ${b.kycStatus === 'Rejected' ? 'border-danger' : 'border-warning'}`}
            >
              <div className="d-flex justify-content-between align-items-start mb-2">
                <h5 className="mb-0">
                  <i className="bi bi-shield-exclamation me-2 text-warning"></i>
                  KYC {b.kycStatus} — {b.businessName}
                </h5>
                <span className={`badge text-bg-${b.kycStatus === 'Rejected' ? 'danger' : 'warning'}`}>
                  {b.kycStatus}
                </span>
              </div>

              {b.kycStatus === 'Rejected' && b.kycRemarks && (
                <div className="alert alert-danger py-2 px-3 mb-2 small">
                  <i className="bi bi-x-circle me-1"></i>
                  Rejection reason: <em>{b.kycRemarks}</em>
                </div>
              )}

              <p className="mb-2 text-muted small">
                {b.kycStatus === 'Rejected'
                  ? 'Your KYC was rejected. Address the reason above, re-upload the required documents, and an Admin will re-review.'
                  : 'Your KYC is pending admin verification. Ensure the following documents are uploaded:'}
              </p>

              <ul className="small mb-3">
                {REQUIRED_KYC_DOCS.map((d) => <li key={d}>{d}</li>)}
              </ul>

              <Link to="/applicant/kyc" className="btn btn-sm btn-warning">
                <i className="bi bi-shield-check me-1"></i>Complete KYC
              </Link>
            </div>
          ))}

          {/* ── No business registered ── */}
          {loaded && businesses.length === 0 && (
            <div className="bk-card p-4 mb-4">
              <div className="d-flex align-items-center gap-3 mb-3">
                <div className="bk-login-feature-icon">
                  <i className="bi bi-building-add"></i>
                </div>
                <div>
                  <h5 className="mb-1">No Business Registered</h5>
                  <p className="text-muted small mb-0">
                    You need to register a business and complete KYC before you can apply for a loan.
                  </p>
                </div>
              </div>
              <Link to="/applicant/register-business" className="btn btn-bk-primary">
                <i className="bi bi-building-add me-1"></i>Register a Business
              </Link>
            </div>
          )}

          {/* ── Dashboard cards ── */}
          <div className="row g-3">

            {/* New Application — blocked if KYC not verified */}
            <div className="col-md-3">
              {hasVerifiedBusiness ? (
                <DashboardCard
                  to="/applicant/apply"
                  icon="bi-file-earmark-plus"
                  title="New Application"
                  description="Start a new loan application for your verified business."
                />
              ) : (
                <div className="bk-card h-100 p-3 bk-dashboard-card-disabled">
                  <div className="bk-nav-card-icon mb-3">
                    <i className="bi bi-file-earmark-plus"></i>
                  </div>
                  <h5 className="bk-dashboard-card-disabled-title mb-1">New Application</h5>
                  <p className="text-muted small mb-2">Start a new loan application for your business.</p>
                  <div className="d-flex align-items-center gap-1 bk-kyc-lock-badge">
                    <i className="bi bi-lock-fill"></i>
                    <span>KYC Verification Required</span>
                  </div>
                </div>
              )}
            </div>

            <div className="col-md-3">
              <DashboardCard
                to="/applicant/kyc"
                icon="bi-shield-check"
                title="My Business & KYC"
                description="Complete KYC verification for your business."
              />
            </div>
            <div className="col-md-3">
              <DashboardCard
                to="/applicant/tracker"
                icon="bi-clipboard-check"
                title="Application Tracker"
                description="Check the status of your applications."
              />
            </div>
            <div className="col-md-3">
              <DashboardCard
                to="/applicant/facility"
                icon="bi-bank"
                title="My Facility"
                description="View your sanctioned facility and request a drawdown."
              />
            </div>
            <div className="col-md-3">
              <DashboardCard
                to="/applicant/portfolio"
                icon="bi-pie-chart"
                title="My Portfolio"
                description="Your total borrowing position across all facilities."
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
