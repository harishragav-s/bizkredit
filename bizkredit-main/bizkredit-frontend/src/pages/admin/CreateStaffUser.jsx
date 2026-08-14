import React, { useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import authService from '../../services/authService';

const STAFF_ROLES = [
  'CREDIT_ANALYST',
  'UNDERWRITING_MANAGER',
  'RELATIONSHIP_MANAGER',
  'COLLATERAL_EVALUATOR',
  'ADMIN',
];


export default function CreateStaffUser() {
  const [form, setForm] = useState({
    name: '',
    email: '',
    password: '',
    phone: '',
    role: 'CREDIT_ANALYST',
    branchId: '',
  });
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  const handleChange = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    setLoading(true);
    try {
      await authService.registerStaff(form);
      setSuccess(`Staff account created for ${form.name} (${form.role.replaceAll('_', ' ')}).`);
      setForm({ name: '', email: '', password: '', phone: '', role: 'CREDIT_ANALYST', branchId: '' });
    } catch (err) {
      setError(err.response?.data?.message || 'Could not create staff account.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="ADMIN" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Admin Console"
            title="Create Staff User"
            subtitle="Create an account for any internal role. Public signup only ever creates an SME Applicant account."
          />

          {error && <div className="alert alert-danger">{error}</div>}
          {success && <div className="alert alert-success">{success}</div>}

          <form onSubmit={handleSubmit} className="bk-card p-4 row g-3" >
            <div className="col-md-6">
              <label className="bk-label">Name</label>
              <input className="form-control bk-input" required value={form.name} onChange={handleChange('name')} />
            </div>
            <div className="col-md-6">
              <label className="bk-label">Phone</label>
              <input className="form-control bk-input" required value={form.phone} onChange={handleChange('phone')} />
            </div>
            <div className="col-md-6">
              <label className="bk-label">Email</label>
              <input type="email" className="form-control bk-input" required value={form.email} onChange={handleChange('email')} />
            </div>
            <div className="col-md-6">
              <label className="bk-label">Password</label>
              <input type="password" className="form-control bk-input" required value={form.password} onChange={handleChange('password')} />
            </div>
            <div className="col-md-6">
              <label className="bk-label">Role</label>
              <select className="form-select bk-input" value={form.role} onChange={handleChange('role')}>
                {STAFF_ROLES.map((r) => (
                  <option key={r} value={r}>{r.replaceAll('_', ' ')}</option>
                ))}
              </select>
            </div>
            <div className="col-md-6">
              <label className="bk-label">Branch ID (optional)</label>
              <input className="form-control bk-input" value={form.branchId} onChange={handleChange('branchId')} />
            </div>
            <div className="col-12">
              <button type="submit" className="btn btn-bk-primary" disabled={loading}>
                {loading ? 'Creating...' : 'Create Staff Account'}
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
