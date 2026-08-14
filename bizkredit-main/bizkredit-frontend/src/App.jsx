import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthContextProvider, useAuth } from './context/AuthContext';
import ProtectedRoute, { roleHomePath } from './components/ProtectedRoute';

import Login from './pages/Login';
import Register from './pages/Register';

import AdminDashboard from './pages/admin/AdminDashboard';
import UserManagement from './pages/admin/UserManagement';
import CreateStaffUser from './pages/admin/CreateStaffUser';
import KycReview from './pages/admin/KycReview';
import LoanProducts from './pages/admin/LoanProducts';
import AuditLogViewer from './pages/admin/AuditLogViewer';
import ApplicationManagement from './pages/admin/ApplicationManagement';

import ApplicantDashboard from './pages/applicant/ApplicantDashboard';
import ApplicationWizard from './pages/applicant/ApplicationWizard';
import ApplicationTracker from './pages/applicant/ApplicationTracker';
import MyFacility from './pages/applicant/MyFacility';
import MyPortfolio from './pages/applicant/MyPortfolio';
import MyBusinessKyc from './pages/applicant/MyBusinessKyc';
import RegisterBusiness from './pages/applicant/RegisterBusiness';

import FinancialEntry from './pages/analyst/FinancialEntry';
import ProposalBuilder from './pages/analyst/ProposalBuilder';
import AnalystDashboard from './pages/analyst/AnalystDashboard';

import DecisionForm from './pages/underwriting/DecisionForm';

import RMDashboard from './pages/rm/RMDashboard';
import PortfolioSummary from './pages/rm/PortfolioSummary';
import FacilityManagement from './pages/rm/FacilityManagement';
import CovenantTracker from './pages/rm/CovenantTracker';
import EWSBoard from './pages/rm/EWSBoard';

import CollateralRegister from './pages/collateral/CollateralRegister';


function RootRedirect() {
  const { user, isAuthenticated } = useAuth();
  return <Navigate to={isAuthenticated ? roleHomePath(user.role) : '/login'} replace />;
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthContextProvider>
        <Routes>

          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/" element={<RootRedirect />} />

          <Route path="/admin" element={<ProtectedRoute allowedRoles={['ADMIN']}><AdminDashboard /></ProtectedRoute>} />
          <Route path="/admin/users" element={<ProtectedRoute allowedRoles={['ADMIN']}><UserManagement /></ProtectedRoute>} />
          <Route path="/admin/users/create" element={<ProtectedRoute allowedRoles={['ADMIN']}><CreateStaffUser /></ProtectedRoute>} />
          <Route path="/admin/kyc" element={<ProtectedRoute allowedRoles={['ADMIN']}><KycReview /></ProtectedRoute>} />
          <Route path="/admin/products" element={<ProtectedRoute allowedRoles={['ADMIN']}><LoanProducts /></ProtectedRoute>} />
          <Route path="/admin/audit-log" element={<ProtectedRoute allowedRoles={['ADMIN']}><AuditLogViewer /></ProtectedRoute>} />
          <Route path="/admin/applications" element={<ProtectedRoute allowedRoles={['ADMIN']}><ApplicationManagement /></ProtectedRoute>} />

          <Route path="/applicant" element={<ProtectedRoute allowedRoles={['SME_APPLICANT']}><ApplicantDashboard /></ProtectedRoute>} />
          <Route path="/applicant/apply" element={<ProtectedRoute allowedRoles={['SME_APPLICANT']}><ApplicationWizard /></ProtectedRoute>} />
          <Route path="/applicant/tracker" element={<ProtectedRoute allowedRoles={['SME_APPLICANT']}><ApplicationTracker /></ProtectedRoute>} />
          <Route path="/applicant/facility" element={<ProtectedRoute allowedRoles={['SME_APPLICANT']}><MyFacility /></ProtectedRoute>} />
          <Route path="/applicant/portfolio" element={<ProtectedRoute allowedRoles={['SME_APPLICANT']}><MyPortfolio /> </ProtectedRoute>} />
          <Route path="/applicant/kyc" element={<ProtectedRoute allowedRoles={['SME_APPLICANT']}><MyBusinessKyc /></ProtectedRoute>} />
          <Route path="/applicant/register-business" element={<ProtectedRoute allowedRoles={['SME_APPLICANT']}><RegisterBusiness /></ProtectedRoute>} />

          <Route path="/analyst" element={<ProtectedRoute allowedRoles={['CREDIT_ANALYST']}><AnalystDashboard /></ProtectedRoute>} />
          <Route path="/analyst/financials" element={<ProtectedRoute allowedRoles={['CREDIT_ANALYST']}><FinancialEntry /></ProtectedRoute>} />
          <Route path="/analyst/proposals" element={<ProtectedRoute allowedRoles={['CREDIT_ANALYST']}><ProposalBuilder /></ProtectedRoute>} />

          <Route path="/underwriting" element={<ProtectedRoute allowedRoles={['UNDERWRITING_MANAGER']}><DecisionForm /></ProtectedRoute>} />
          <Route path="/underwriting/decisions" element={<ProtectedRoute allowedRoles={['UNDERWRITING_MANAGER']}><DecisionForm /></ProtectedRoute>} />

          <Route path="/rm" element={<ProtectedRoute allowedRoles={['RELATIONSHIP_MANAGER']}><RMDashboard /></ProtectedRoute>} />
          <Route path="/rm/portfolio" element={<ProtectedRoute allowedRoles={['RELATIONSHIP_MANAGER']}><PortfolioSummary /></ProtectedRoute>} />
          <Route path="/rm/facilities" element={<ProtectedRoute allowedRoles={['RELATIONSHIP_MANAGER']}><FacilityManagement /></ProtectedRoute>} />
          <Route path="/rm/covenants" element={<ProtectedRoute allowedRoles={['RELATIONSHIP_MANAGER']}><CovenantTracker /></ProtectedRoute>} />
          <Route path="/rm/ews" element={<ProtectedRoute allowedRoles={['RELATIONSHIP_MANAGER']}><EWSBoard /></ProtectedRoute>} />

          <Route path="/collateral" element={<ProtectedRoute allowedRoles={['COLLATERAL_EVALUATOR']}><CollateralRegister /></ProtectedRoute>} />
          <Route path="/collateral/register" element={<ProtectedRoute allowedRoles={['COLLATERAL_EVALUATOR']}><CollateralRegister /></ProtectedRoute>} />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </AuthContextProvider>
    </BrowserRouter>
  );
}