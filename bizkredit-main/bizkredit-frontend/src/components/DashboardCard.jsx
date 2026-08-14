import React from 'react';
import { Link } from 'react-router-dom';
export default function DashboardCard({ to, icon, title, description }) {
  return (
    <Link to={to} className="bk-nav-card text-decoration-none d-block h-100">
      <div className="bk-nav-card-icon">
        <i className={`bi ${icon}`}></i>
      </div>
      <div className="card-title">{title}</div>
      <div className="card-text">{description}</div>
    </Link>
  );
}
