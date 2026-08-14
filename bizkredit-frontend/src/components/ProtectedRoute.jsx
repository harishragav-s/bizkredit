import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function ProtectedRoute({ children, allowedRoles }) {
  const { user, isAuthenticated } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && !allowedRoles.includes(user.role)) {
    return <Navigate to={roleHomePath(user.role)} replace />;
  }

  return children;
}

export function roleHomePath(role) {
  switch (role) {
    case 'SME_APPLICANT':
      return '/applicant';
    case 'CREDIT_ANALYST':
      return '/analyst';
    case 'UNDERWRITING_MANAGER':
      return '/underwriting';
    case 'RELATIONSHIP_MANAGER':
      return '/rm';
    case 'COLLATERAL_EVALUATOR':
      return '/collateral';
    case 'ADMIN':
      return '/admin';
    default:
      return '/login';
  }
}
