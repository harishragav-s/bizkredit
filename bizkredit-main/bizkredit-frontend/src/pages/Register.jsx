import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import authService from '../services/authService';
import LogoMark from '../components/LogoMark';

export default function Register() {
  const [form, setForm] = useState({
    name: '',
    email: '',
    password: '',
    phone: '',
  });
  const [showPwd, setShowPwd] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleChange = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);
    try {
      await authService.register(form);
      setSuccess('Account created. Redirecting to sign in...');
      setTimeout(() => navigate('/login'), 1200);
    } catch (err) {
      setError(err.response?.data?.message || 'Registration failed.');
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
            Apply for business<br />credit, digitally.
          </h1>
          <div className="bk-login-accent-bar mb-3" />
          <p className="bk-login-hero-desc mb-1">
            Create your applicant account to start a loan application,
            track its progress, and manage your sanctioned facility.
          </p>
        </div>

        {/* Bank staff note */}
        <div>
          <div className="bk-mono mb-2" style={{ textTransform: 'uppercase', letterSpacing: '0.06em' }}>
            Bank staff?
          </div>
          <p style={{ color: 'rgba(255,255,255,0.55)', fontSize: '0.85rem' }}>
            Contact your Admin for account access.
          </p>
        </div>
      </div>


      <div className="flex-grow-1 d-flex align-items-center justify-content-center bk-login-form-panel px-3">
        <div className="bk-login-card w-100">

          <div className="text-center mb-3">
            <div className="bk-login-bank-icon mx-auto">
              <i className="bi bi-bank"></i>
            </div>
          </div>

          <div className="bk-page-eyebrow text-center">Create account</div>
          <h2 className="text-center bk-login-card-title mb-1">Join BizKredit</h2>
          <p className="text-center bk-login-card-sub mb-4">
            Set up your SME applicant account.
          </p>


          {error && (
            <div className="alert alert-danger bk-login-alert d-flex align-items-center gap-2 mb-3">
              <i className="bi bi-exclamation-circle-fill"></i>
              <span>{error}</span>
            </div>
          )}


          {success && (
            <div className="alert alert-success bk-login-alert d-flex align-items-center gap-2 mb-3">
              <i className="bi bi-check-circle-fill"></i>
              <span>{success}</span>
            </div>
          )}

          <form onSubmit={handleSubmit}>


            <div className="mb-3">
              <label className="bk-login-label" htmlFor="regName">
                Name
              </label>
              <div className="input-group">
                <span className="input-group-text bk-login-input-icon">
                  <i className="bi bi-person"></i>
                </span>
                <input
                  id="regName"
                  type="text"
                  className="form-control bk-login-input"
                  placeholder="Enter your full name"
                  value={form.name}
                  onChange={handleChange('name')}
                  required
                />
              </div>
            </div>

            {/* Phone */}
            <div className="mb-3">
              <label className="bk-login-label" htmlFor="regPhone">
                Phone
              </label>
              <div className="input-group">
                <span className="input-group-text bk-login-input-icon">
                  <i className="bi bi-telephone"></i>
                </span>
                <input
                  id="regPhone"
                  type="tel"
                  className="form-control bk-login-input"
                  placeholder="Enter your phone number"
                  value={form.phone}
                  onChange={handleChange('phone')}
                  required
                />
              </div>
            </div>


            <div className="mb-3">
              <label className="bk-login-label" htmlFor="regEmail">
                Email
              </label>
              <div className="input-group">
                <span className="input-group-text bk-login-input-icon">
                  <i className="bi bi-envelope"></i>
                </span>
                <input
                  id="regEmail"
                  type="email"
                  className="form-control bk-login-input"
                  placeholder="Enter your email"
                  value={form.email}
                  onChange={handleChange('email')}
                  required
                />
              </div>
            </div>


            <div className="mb-4">
              <label className="bk-login-label" htmlFor="regPassword">
                Password
              </label>
              <div className="input-group">
                <span className="input-group-text bk-login-input-icon">
                  <i className="bi bi-lock"></i>
                </span>
                <input
                  id="regPassword"
                  type={showPwd ? 'text' : 'password'}
                  className="form-control bk-login-input"
                  placeholder="Create a password"
                  value={form.password}
                  onChange={handleChange('password')}
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
                  Creating account...
                </>
              ) : (
                <>
                  <i className="bi bi-arrow-right"></i>
                  Create account
                </>
              )}
            </button>

          </form>

          <p className="text-center bk-login-footer-text mt-3 mb-0">
            Already have an account?{' '}
            <Link to="/login" className="bk-login-footer-link">
              Sign in
            </Link>
          </p>

        </div>
      </div>
    </div>
  );
}