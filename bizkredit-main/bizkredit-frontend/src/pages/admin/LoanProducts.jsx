import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import SmartAmountInput from '../../components/SmartAmountInput';
import smeService from '../../services/smeService';
import { useAuth } from '../../context/AuthContext';
import { formatINR } from '../../utils/currency';
import { nullifyEmptyStrings } from '../../utils/forms';

const PRODUCT_TYPES = ['TERM_LOAN', 'WORKING_CAPITAL_CC', 'OVERDRAFT_FACILITY', 'INVOICE_FINANCING', 'EQUIPMENT_LOAN'];

export default function LoanProducts() {
  const { user } = useAuth();
  const [products, setProducts] = useState([]);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [form, setForm] = useState({
    productCode: '',
    productName: '',
    productType: 'TERM_LOAN',
    minAmount: '',
    maxAmount: '',
    minTenure: '',
    maxTenure: '',
    baseInterestRate: '',
  });

  const loadProducts = () => {
    smeService.getProducts().then((res) => setProducts(res.data.data));
  };

  useEffect(() => {
    loadProducts();
  }, []);

  const handleChange = (field) => (e) => setForm({ ...form, [field]: e.target.value });

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setSuccess('');
    try {
      await smeService.createProduct(nullifyEmptyStrings(form), user.userId);
      setSuccess(`Product "${form.productName}" created.`);
      loadProducts();
      setForm({
        productCode: '', productName: '', productType: 'TERM_LOAN',
        minAmount: '', maxAmount: '', minTenure: '', maxTenure: '', baseInterestRate: '',
      });
    } catch (err) {
      setError(err.response?.data?.message || 'Could not create product.');
    }
  };

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="ADMIN" />
        <div className="bk-content">
          <PageHeader eyebrow="Admin Console" title="Loan Products" />

          {error && <div className="alert alert-danger">{error}</div>}
          {success && <div className="alert alert-success">{success}</div>}

          <form onSubmit={handleSubmit} className="bk-card p-4 row g-3 mb-4" >
            <div className="col-md-3">
              <label className="bk-label">Product Code</label>
              <input className="form-control bk-input" required value={form.productCode} onChange={handleChange('productCode')} />
            </div>
            <div className="col-md-5">
              <label className="bk-label">Product Name</label>
              <input className="form-control bk-input" required value={form.productName} onChange={handleChange('productName')} />
            </div>
            <div className="col-md-4">
              <label className="bk-label">Product Type</label>
              <select className="form-select bk-input" value={form.productType} onChange={handleChange('productType')}>
                {PRODUCT_TYPES.map((t) => <option key={t} value={t}>{t.replaceAll('_', ' ')}</option>)}
              </select>
            </div>
            <div className="col-md-3">
              <label className="bk-label">Min Amount</label>
              <SmartAmountInput value={form.minAmount} onChange={handleChange('minAmount')} />
            </div>
            <div className="col-md-3">
              <label className="bk-label">Max Amount</label>
              <SmartAmountInput value={form.maxAmount} onChange={handleChange('maxAmount')} />
            </div>
            <div className="col-md-3">
              <label className="bk-label">Min Tenure</label>
              <input type="number" className="form-control bk-input" value={form.minTenure} onChange={handleChange('minTenure')} />
            </div>
            <div className="col-md-3">
              <label className="bk-label">Max Tenure</label>
              <input type="number" className="form-control bk-input" value={form.maxTenure} onChange={handleChange('maxTenure')} />
            </div>
            <div className="col-md-4">
              <label className="bk-label">Base Interest Rate (%)</label>
              <input type="number" step="0.01" className="form-control bk-input" value={form.baseInterestRate} onChange={handleChange('baseInterestRate')} />
            </div>
            <div className="col-12">
              <button type="submit" className="btn btn-bk-primary">Create Product</button>
            </div>
          </form>

          <table className="table bk-table">
            <thead>
              <tr><th>Code</th><th>Name</th><th>Type</th><th>Amount Range</th><th>Rate</th><th>Status</th></tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr key={p.productId}>
                  <td>{p.productCode}</td>
                  <td>{p.productName}</td>
                  <td>{p.productType?.replaceAll('_', ' ')}</td>
                  <td>{formatINR(p.minAmount)} - {formatINR(p.maxAmount)}</td>
                  <td>{p.baseInterestRate}%</td>
                  <td>
                    <span className={`badge text-bg-${p.status === 'ACTIVE' ? 'success' : 'neutral'}`}>
                      {p.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}