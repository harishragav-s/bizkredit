import React, { useEffect, useState } from 'react';
import Navbar from '../../components/Navbar';
import Sidebar from '../../components/Sidebar';
import PageHeader from '../../components/PageHeader';
import smeService from '../../services/smeService';
import { useAuth } from '../../context/AuthContext';
import creditService from '../../services/creditService';
import collateralService from '../../services/collateralService';
import { formatINR } from '../../utils/currency';

const PIPELINE_STEPS = [
  { key: 'DRAFT',                  label: 'Draft',              icon: 'bi-pencil',           desc: 'Application saved but not submitted.' },
  { key: 'SUBMITTED',              label: 'Submitted',          icon: 'bi-send',              desc: 'Awaiting a Credit Analyst to pick up.' },
  { key: 'IN_REVIEW',              label: 'Credit Analysis',    icon: 'bi-bar-chart',         desc: 'Analyst reviewing financials & documents.' },
  { key: 'UNDERWRITING_APPROVAL',  label: 'Underwriting',       icon: 'bi-shield-check',      desc: 'Underwriting Manager making a sanction decision.' },
  { key: 'SANCTIONED',             label: 'Sanctioned',         icon: 'bi-check-circle',      desc: 'Approved! Relationship Manager will set up your facility.' },
  { key: 'DISBURSED',              label: 'Disbursed',          icon: 'bi-bank',              desc: 'Facility active. Funds disbursed.' },
];

const STATUS_INDEX = Object.fromEntries(PIPELINE_STEPS.map((s, i) => [s.key, i]));

const STATUS_BADGE = {
  DRAFT: 'neutral', SUBMITTED: 'info', IN_REVIEW: 'warning',
  UNDERWRITING_APPROVAL: 'warning', SANCTIONED: 'success', REJECTED: 'danger', DISBURSED: 'info',
};

export default function ApplicationTracker() {
  const { user } = useAuth();
  const [applications, setApplications] = useState([]);
  const [details, setDetails] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const reloadCollateral = async (applicationId) => {
    try {
      const res = await collateralService.getCollateralsByApplication(applicationId);
      setDetails((prev) => ({ ...prev, [applicationId]: { ...prev[applicationId], collaterals: res.data.data } }));
    } catch { /* leave as-is */ }
  };

  useEffect(() => {
    smeService
      .getApplications({ applicantUserId: user.userId })
      .then(async (res) => {
        const apps = res.data.data;
        setApplications(apps);

        const detailEntries = await Promise.all(
          apps.map(async (app) => {
            const detail = { proposal: null, decision: null, facility: null, collaterals: [], business: null };

            if (app.businessId) {
              try {
                const bizRes = await smeService.getBusiness(app.businessId);
                detail.business = bizRes.data.data;
              } catch { /* silent */ }
            }

            try {
              const proposalsRes = await creditService.getProposalsByApplication(app.applicationId);
              const proposals = proposalsRes.data.data;
              if (proposals.length > 0) {
                // Every proposal, each with its own decision if one exists -
                // previously only the LATEST proposal was kept and every
                // earlier round was silently discarded, so an applicant
                // whose application went through more than one proposal
                // (e.g. declined, then re-proposed with different terms)
                // never saw that history at all.
                detail.proposals = await Promise.all(proposals.map(async (p) => {
                  try {
                    const decisionRes = await creditService.getDecisionByProposal(p.proposalId);
                    return { ...p, decision: decisionRes.data.data };
                  } catch {
                    return { ...p, decision: null };
                  }
                }));
                // Kept for any other code still reading detail.proposal/
                // detail.decision directly - always the most recent round.
                detail.proposal = detail.proposals[detail.proposals.length - 1];
                detail.decision = detail.proposal.decision;
              }
            } catch { /* no proposal yet */ }

            try {
              const collateralsRes = await collateralService.getCollateralsByApplication(app.applicationId);
              detail.collaterals = collateralsRes.data.data;
            } catch { /* no collateral yet */ }

            if (app.businessId && (app.status === 'SANCTIONED' || app.status === 'DISBURSED')) {
              try {
                const facilitiesRes = await collateralService.getFacilitiesByBusiness(app.businessId);
                const facilities = facilitiesRes.data.data;
                if (facilities.length > 0) detail.facility = facilities[facilities.length - 1];
              } catch { /* no facility yet */ }
            }

            return [app.applicationId, detail];
          })
        );

        setDetails(Object.fromEntries(detailEntries));
      })
      .catch((err) => setError(err.response?.data?.message || 'Could not load applications.'))
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="bk-app-shell">
      <Navbar />
      <div className="bk-body">
        <Sidebar role="SME_APPLICANT" />
        <div className="bk-content">
          <PageHeader
            eyebrow="Applicant Portal"
            title="Application Tracker"
            subtitle="Track where each application is in the loan process — from submission through disbursement."
          />

          {error && <div className="alert alert-danger">{error}</div>}
          {success && <div className="alert alert-success">{success}</div>}
          {loading && <p className="text-muted">Loading your applications...</p>}

          {!loading && applications.length === 0 && (
            <div className="bk-empty">
              <i className="bi bi-inbox"></i>
              No applications yet. Start a new one from the dashboard.
            </div>
          )}

          {!loading && applications.map((app) => {
            const detail = details[app.applicationId] || {};
            const isRejected = app.status === 'REJECTED';
            const currentStepIdx = isRejected ? -1 : (STATUS_INDEX[app.status] ?? 0);

            return (
              <div className="bk-card mb-4" key={app.applicationId} style={{ maxWidth: '860px', overflow: 'hidden' }}>
                {/* Header */}
                <div className="p-4 pb-3 border-bottom">
                  <div className="d-flex justify-content-between align-items-start">
                    <div>
                      <h5 className="mb-1" >
                        Application #{app.applicationId} — {app.productType?.replaceAll('_', ' ')}
                      </h5>
                      <p className="text-muted mb-0" >
                        {formatINR(app.requestedAmount)} &middot; {app.tenure} months &middot; Applied {app.applicationDate}
                      </p>
                    </div>
                    <span className={`badge text-bg-${STATUS_BADGE[app.status] || 'neutral'}`}>
                      {app.status?.replaceAll('_', ' ')}
                    </span>
                  </div>
                </div>

                {/* Pipeline flow diagram */}
                {isRejected ? (
                  <div className="px-4 py-3" style={{ background: '#fff8f8' }}>
                    <div className="d-flex align-items-center gap-2" >
                      <i className="bi bi-x-circle-fill"></i>
                      <strong>Application Rejected</strong>
                    </div>
                    {detail.decision?.specialConditions && (
                      <p className="text-muted mt-1 mb-0 small">
                        Reason: {detail.decision.specialConditions}
                      </p>
                    )}
                  </div>
                ) : (
                  <div className="px-4 py-3 border-bottom">
                    <div className="d-flex align-items-center gap-0" style={{ overflowX: 'auto' }}>
                      {PIPELINE_STEPS.map((step, idx) => {
                        const isDone = idx < currentStepIdx;
                        const isCurrent = idx === currentStepIdx;
                        const isPending = idx > currentStepIdx;
                        return (
                          <React.Fragment key={step.key}>
                            <div
                              style={{
                                display: 'flex', flexDirection: 'column', alignItems: 'center',
                                minWidth: '90px', position: 'relative',
                              }}
                              title={step.desc}
                            >
                              <div style={{
                                width: '36px', height: '36px', borderRadius: '50%',
                                display: 'flex', alignItems: 'center', justifyContent: 'center',
                                background: isCurrent ? 'var(--bk-gold)' : isDone ? 'var(--bk-navy)' : '#e8e8e8',
                                color: isCurrent ? 'var(--bk-navy)' : isDone ? '#fff' : '#bbb',
                                border: isCurrent ? '3px solid var(--bk-navy)' : 'none',
                                boxShadow: isCurrent ? '0 0 0 3px rgba(200,155,60,0.2)' : 'none',
                                transition: 'all 0.2s',
                              }}>
                                {isDone
                                  ? <i className="bi bi-check-lg"></i>
                                  : <i className={step.icon}></i>
                                }
                              </div>
                              <div style={{ fontWeight: isCurrent ? 700 : 400,
                                color: isCurrent ? 'var(--bk-navy)' : isDone ? '#555' : '#aaa',
                                textAlign: 'center', marginTop: '4px', lineHeight: 1.3,
                                textTransform: 'uppercase', letterSpacing: '0.03em',
                                maxWidth: '80px',
                              }}>
                                {step.label}
                              </div>
                            </div>
                            {idx < PIPELINE_STEPS.length - 1 && (
                              <div style={{
                                flex: 1, height: '2px', minWidth: '16px',
                                background: idx < currentStepIdx ? 'var(--bk-navy)' : '#e0e0e0',
                                marginBottom: '18px',
                              }} />
                            )}
                          </React.Fragment>
                        );
                      })}
                    </div>
                    {/* Current step description */}
                    <p className="mb-0 mt-2" >
                      <i className="bi bi-arrow-right me-1" ></i>
                      {PIPELINE_STEPS[currentStepIdx]?.desc || ''}
                    </p>
                  </div>
                )}

                {/* KYC alert */}
                {detail.business && detail.business.kycStatus !== 'Verified' && (
                  <div className={`mx-4 mt-3 alert py-2 px-3 small text-muted ${detail.business.kycStatus === 'Rejected' ? 'alert-danger' : 'alert-warning'}`}>
                    <strong>KYC {detail.business.kycStatus}.</strong>{' '}
                    {detail.business.kycStatus === 'Rejected'
                      ? <>Rejected{detail.business.kycRemarks ? `: ${detail.business.kycRemarks}` : ''}. Re-upload your documents from My Business &amp; KYC — an Admin will re-review.</>
                      : <>Your application cannot progress until an Admin verifies your business KYC. Make sure all required documents (PAN Card, GST Returns, Audited Financials) are uploaded.</>
                    }
                  </div>
                )}

                {/* Detail grid */}
                <div className="p-4 pt-3">
                  <div className="row g-3">
                    <div className="col-md-3">
                      <div className="bk-label mb-1" >Credit Analysis</div>
                      {detail.proposal ? (
                        <div className="small text-muted">
                          Proposal #{detail.proposal.proposalId}<br />
                          {formatINR(detail.proposal.suggestedAmount)}
                          {detail.proposal.ratingLabel && <> · <strong>{detail.proposal.ratingLabel}</strong></>}
                          <br />
                          <span className="text-muted" >
                            {detail.proposal.status?.replaceAll('_', ' ')}
                          </span>
                        </div>
                      ) : (
                        <span className="text-muted small">Pending</span>
                      )}
                    </div>

                    <div className="col-md-3">
                      <div className="bk-label mb-1" >Underwriting Decision</div>
                      {detail.decision ? (
                        <div className="small text-muted">
                          <strong>{detail.decision.status?.replaceAll('_', ' ')}</strong>
                          {detail.decision.sanctionedAmount && <> · {formatINR(detail.decision.sanctionedAmount)}</>}
                          {detail.decision.approvedRate && <> @ {detail.decision.approvedRate}%</>}
                          <br />
                          <span className="text-muted" >{detail.decision.decisionDate}</span>
                          {detail.decision.specialConditions && (
                            <><br /><span className="text-muted" >Conditions: {detail.decision.specialConditions}</span></>
                          )}
                        </div>
                      ) : (
                        <span className="text-muted small">Pending</span>
                      )}
                    </div>

                    <div className="col-md-3">
                      <div className="bk-label mb-1" >Collateral</div>
                      {detail.collaterals?.length > 0 ? (
                        <div className="small text-muted">
                          {detail.collaterals.map((c) => (
                            <div key={c.collateralId} className="mb-1">
                              {c.assetType?.replaceAll('_', ' ')} · {formatINR(c.status === 'DISCLOSED' ? c.marketValue : c.realisableValue)}
                              {' '}
                              <span className={`badge text-bg-${c.status === 'DISCLOSED' ? 'warning' : 'success'}`} style={{ fontSize: '0.65rem' }}>
                                {c.status === 'DISCLOSED' ? 'Awaiting evaluation' : c.status}
                              </span>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <span className="text-muted small">Not disclosed yet</span>
                      )}
                    </div>

                    <div className="col-md-3">
                      <div className="bk-label mb-1" >Facility</div>
                      {detail.facility ? (
                        <div className="small text-muted">
                          Facility #{detail.facility.facilityId}<br />
                          Sanctioned: {formatINR(detail.facility.sanctionedLimit)}<br />
                          Outstanding: {formatINR(detail.facility.outstandingBalance)}
                        </div>
                      ) : (
                        <span className="text-muted small">
                          {app.status === 'SANCTIONED'
                            ? 'Being set up by your Relationship Manager'
                            : 'Not yet created'}
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Full proposal history - previously only the latest
                      round was ever shown, so a re-proposed application
                      (declined once, re-submitted with different terms)
                      had no visible history at all. */}
                  {detail.proposals?.length > 1 && (
                    <div className="mt-3">
                      <div className="bk-label mb-2">Proposal History ({detail.proposals.length} rounds)</div>
                      <table className="table table-sm table-hover align-middle">
                        <thead>
                          <tr><th>Round</th><th>Amount</th><th>Rating</th><th>Proposal Status</th><th>Decision</th></tr>
                        </thead>
                        <tbody>
                          {detail.proposals.map((p, i) => (
                            <tr key={p.proposalId}>
                              <td>#{i + 1}</td>
                              <td>{formatINR(p.suggestedAmount)}</td>
                              <td>{p.ratingLabel || '—'}</td>
                              <td><span className={`badge ${p.status === 'SUBMITTED' ? 'bg-info' : p.status === 'APPROVED_BY_MANAGER' ? 'bg-success' : 'bg-secondary'}`}>{p.status?.replaceAll('_', ' ')}</span></td>
                              <td>
                                {p.decision ? (
                                  <span className={`badge ${p.decision.status === 'DECLINED' ? 'bg-danger' : p.decision.status === 'CONDITIONAL_APPROVAL' ? 'bg-warning text-dark' : 'bg-success'}`}>
                                    {p.decision.status?.replaceAll('_', ' ')}
                                  </span>
                                ) : (
                                  <span className="text-muted small">Pending</span>
                                )}
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}

                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
