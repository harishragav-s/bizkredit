import React from 'react';
import { NavLink } from 'react-router-dom';

const LINKS = {
  SME_APPLICANT: [
    { to: '/applicant',                   label: 'Dashboard',            icon: 'bi-grid' },
    { to: '/applicant/register-business', label: 'Register Business',    icon: 'bi-building-add' },
    { to: '/applicant/kyc',               label: 'My Business & KYC',    icon: 'bi-shield-check' },
    { to: '/applicant/apply',             label: 'New Application',      icon: 'bi-file-earmark-plus', kycGated: true },
    { to: '/applicant/tracker',           label: 'Application Tracker',  icon: 'bi-clipboard-check' },
    { to: '/applicant/facility',          label: 'My Facility',          icon: 'bi-bank' },
    { to: '/applicant/portfolio',         label: 'My Portfolio',         icon: 'bi-pie-chart' },
  ],
  CREDIT_ANALYST: [
    { to: '/analyst',            label: 'Dashboard',        icon: 'bi-grid' },
    { to: '/analyst/financials', label: 'Financial Entry',  icon: 'bi-graph-up' },
    { to: '/analyst/proposals',  label: 'Credit Proposals', icon: 'bi-file-earmark-text' },
  ],
  UNDERWRITING_MANAGER: [
    { to: '/underwriting', label: 'Sanction Decisions', icon: 'bi-shield-check' },
  ],
  RELATIONSHIP_MANAGER: [
    { to: '/rm',              label: 'Dashboard',        icon: 'bi-grid' },
    { to: '/rm/portfolio',    label: 'Portfolio Overview',icon: 'bi-pie-chart' },
    { to: '/rm/facilities',   label: 'Facilities',        icon: 'bi-bank' },
    { to: '/rm/covenants',    label: 'Covenant Tracker',  icon: 'bi-journal-check' },
    { to: '/rm/ews',          label: 'EWS Board',         icon: 'bi-exclamation-triangle' },
  ],
  COLLATERAL_EVALUATOR: [
    { to: '/collateral', label: 'Collateral Register', icon: 'bi-house-gear' },
  ],
  ADMIN: [
    { to: '/admin',                label: 'Dashboard',              icon: 'bi-grid' },
    { to: '/admin/users',          label: 'User Management',        icon: 'bi-people' },
    { to: '/admin/users/create',   label: 'Create Staff User',      icon: 'bi-person-plus' },
    { to: '/admin/applications',   label: 'Applications',           icon: 'bi-file-earmark-text' },
    { to: '/admin/kyc',            label: 'KYC Review',             icon: 'bi-person-check' },
    { to: '/admin/products',       label: 'Loan Products',          icon: 'bi-box-seam' },
    { to: '/admin/audit-log',      label: 'Audit Trail',            icon: 'bi-clock-history' },
  ],
};

export default function Sidebar({ role, kycVerified }) {
  const links = LINKS[role] || [];

  return (
    <div className="bk-sidebar">
      <ul className="nav flex-column">
        {links.map((link) => {
          // For KYC-gated links, show a disabled state if KYC not verified
          const isLocked = link.kycGated && kycVerified === false;

          if (isLocked) {
            return (
              <li className="nav-item" key={link.to}>
                <span className="bk-sidebar-link bk-sidebar-link-locked" title="Complete KYC to unlock">
                  <i className={`bi ${link.icon}`}></i>
                  {link.label}
                  <i className="bi bi-lock-fill ms-auto bk-sidebar-lock-icon"></i>
                </span>
              </li>
            );
          }

          return (
            <li className="nav-item" key={link.to}>
              <NavLink
                to={link.to}
                end
                className={({ isActive }) => 'bk-sidebar-link' + (isActive ? ' active' : '')}
              >
                <i className={`bi ${link.icon}`}></i>
                {link.label}
              </NavLink>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
