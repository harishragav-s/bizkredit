import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import smeService from '../../services/smeService';
import { formatINR } from '../../utils/currency';

const STATUS_BADGE = {
  DRAFT: 'neutral',
  SUBMITTED: 'info',
  IN_REVIEW: 'warning',
  UNDERWRITING_APPROVAL: 'warning',
  SANCTIONED: 'success',
  REJECTED: 'danger',
  DISBURSED: 'info',
};


function BusinessNameCell({ businessId }) {
  const [name, setName] = useState(businessId ? 'Loading...' : '—');

  useEffect(() => {
    if (!businessId) return;

    smeService.getBusiness(businessId)
      .then((response) => {
        setName(response.data.data.businessName);
      })
      .catch(() => {
        setName(`Business #${businessId}`);
      });

  }, [businessId]);

  return <span>{name}</span>;
}


export default function ApplicationManagement() {
  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState('');
  const [filterStatus, setFilterStatus] = useState('');

  useEffect(() => {
    setLoading(true);
    setErrorMessage('');

    const queryParams = filterStatus ? { status: filterStatus } : {};


    smeService.getApplications(queryParams)
      .then((response) => {
        setApplications(response.data.data);
      })
      .catch((error) => {
        setErrorMessage(error.response?.data?.message || 'Could not load applications.');
      })
      .finally(() => {
        setLoading(false);
      });

  }, [filterStatus]);

  const statuses = ['', 'DRAFT', 'SUBMITTED', 'IN_REVIEW', 'UNDERWRITING_APPROVAL', 'SANCTIONED', 'REJECTED', 'DISBURSED'];

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="ADMIN" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Admin Console"
            title="Application Management"
            subtitle="View all loan applications across the platform."
          />

          {errorMessage && <div className="alert alert-danger">{errorMessage}</div>}

          <div className="d-flex align-items-center gap-3 mb-4">
            <div style={{ minWidth: '220px' }}>
              <label className="bk-label">Filter by Status</label>
              <select
                className="form-select bk-input"
                value={filterStatus}
                onChange={(event) => setFilterStatus(event.target.value)}
              >
                {statuses.map((statusItem) => (
                  <option key={statusItem} value={statusItem}>
                    {statusItem ? statusItem.replaceAll('_', ' ') : 'All Statuses'}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {loading && <p className="text-muted">Loading...</p>}

          {!loading && applications.length === 0 && (
            <div className="bk-empty">
              <i className="bi bi-inbox"></i>
              No applications found.
            </div>
          )}

          {!loading && applications.length > 0 && (
            <table className="table bk-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Business</th>
                  <th>Product</th>
                  <th>Amount</th>
                  <th>Applied</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {applications.map((application) => (
                  <tr key={application.applicationId}>
                    <td className="bk-mono">#{application.applicationId}</td>

                    {/* Render the self-loading component here */}
                    <td>
                      <BusinessNameCell businessId={application.businessId} />
                    </td>

                    <td>{application.productType?.replaceAll('_', ' ')}</td>
                    <td>{application.requestedAmount != null ? formatINR(application.requestedAmount) : '—'}</td>
                    <td>{application.applicationDate || '—'}</td>
                    <td>
                      <span className={`badge text-bg-${STATUS_BADGE[application.status] || 'neutral'}`}>
                        {application.status?.replaceAll('_', ' ')}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}