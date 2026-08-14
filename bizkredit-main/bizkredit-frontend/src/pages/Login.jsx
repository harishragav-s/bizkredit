import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { roleHomePath } from '../components/ProtectedRoute';
import LogoMark from '../components/LogoMark';

export default function Login() {
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [showPwd, setShowPwd]   = useState(false);
  const [error, setError]       = useState('');
  const [loading, setLoading]   = useState(false);
  const { login }  = useAuth();
  const navigate   = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const user = await login(email, password);
      navigate(roleHomePath(user.role));
    } catch (err) {
      setError(err.response?.data?.message || 'Login failed. Check your credentials.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="container-fluid vh-100 p-0 d-flex">


      <div className="bk-login-brand d-none d-lg-flex flex-column justify-content-between p-5">


        <div className="d-flex align-items-center gap-2">
          <LogoMark size={44} />
          <div>
            <div className="bk-login-brand-name">BIZKREDIT</div>
            <div className="bk-login-brand-tagline">BANKING SOLUTIONS</div>
          </div>
        </div>

        <div>
          <h1 className="bk-login-hero-title">
            Empowering SMEs,<br />Building Tomorrow
          </h1>
          <div className="bk-login-accent-bar mb-3" />
          <p className="bk-login-hero-desc mb-1">
            BizKredit SME Loan Management System
          </p>
          <p className="bk-login-hero-sub mb-0">
            End-to-end lending lifecycle management
          </p>
        </div>

        <div className="d-flex gap-4">
          {[
            { icon: 'bi-people',             label: 'Customer', sub: 'Onboarding'  },
            { icon: 'bi-file-earmark-check', label: 'Loan',     sub: 'Origination' },
            { icon: 'bi-bar-chart',          label: 'Credit',   sub: 'Assessment'  },
            { icon: 'bi-shield-check',       label: 'Risk',     sub: 'Monitoring'  },
          ].map((f) => (
            <div key={f.label} className="text-center">
              <div className="bk-login-feature-icon mx-auto mb-2">
                <i className={`bi ${f.icon}`}></i>
              </div>
              <div className="bk-login-feature-label">{f.label}</div>
              <div className="bk-login-feature-sub">{f.sub}</div>
            </div>
          ))}
        </div>
      </div>


      <div className="flex-grow-1 d-flex align-items-center justify-content-center bk-login-form-panel px-3">
        <div className="bk-login-card w-100">


          <div className="text-center mb-3">
            <div className="bk-login-bank-icon mx-auto">
              <i className="bi bi-bank"></i>
            </div>
          </div>

          <h2 className="text-center bk-login-card-title mb-1">Welcome Back</h2>
          <p className="text-center bk-login-card-sub mb-4">
            Sign in to access your account
          </p>


          {error && (
            <div className="alert alert-danger bk-login-alert d-flex align-items-center gap-2 mb-3">
              <i className="bi bi-exclamation-circle-fill"></i>
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleSubmit}>


            <div className="mb-3">
              <label className="bk-login-label" htmlFor="loginEmail">
                Email Address
              </label>
              <div className="input-group">
                <span className="input-group-text bk-login-input-icon">
                  <i className="bi bi-person"></i>
                </span>
                <input
                  id="loginEmail"
                  type="email"
                  className="form-control bk-login-input"
                  placeholder="Enter your email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </div>
            </div>


            <div className="mb-3">
              <label className="bk-login-label" htmlFor="loginPassword">
                Password
              </label>
              <div className="input-group">
                <span className="input-group-text bk-login-input-icon">
                  <i className="bi bi-lock"></i>
                </span>
                <input
                  id="loginPassword"
                  type={showPwd ? 'text' : 'password'}
                  className="form-control bk-login-input"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
                <button
                  type="button"
                  className="input-group-text bk-login-eye-btn"
                  onClick={() => setShowPwd((v) => !v)}
                  aria-label={showPwd ? 'Hide password' : 'Show password'}
                >
                  <i className={`bi ${showPwd ? 'bi-eye-slash' : 'bi-eye'}`}></i>
                </button>
              </div>
            </div>



            <button
              type="submit"
              className="btn bk-login-btn w-100 d-flex align-items-center justify-content-center gap-2"
              disabled={loading}
            >
              {loading ? (
                <>
                  <span className="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span>
                  Signing in...
                </>
              ) : (
                <>
                  <i className="bi bi-box-arrow-in-right"></i>
                  Login
                </>
              )}
            </button>

          </form>

          <p className="text-center bk-login-footer-text mt-3 mb-0">
            Don't have an account?{' '}
            <Link to="/register" className="bk-login-footer-link">
              Sign up
            </Link>
          </p>

        </div>
      </div>
    </div>
  );
}
