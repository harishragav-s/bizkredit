package com.bizkredit.credit.service;

import com.bizkredit.credit.client.SmeGateway;
import com.bizkredit.credit.dto.LoanApplicationDTO;
import com.bizkredit.credit.enums.ApplicationStatus;
import com.bizkredit.credit.enums.DecisionStatus;
import com.bizkredit.credit.enums.NotificationCategory;
import com.bizkredit.credit.enums.ProductType;
import com.bizkredit.credit.enums.ProposalStatus;
import com.bizkredit.credit.entity.FinancialStatement;
import com.bizkredit.credit.entity.CreditProposal;
import com.bizkredit.credit.entity.UnderwritingDecision;
import com.bizkredit.credit.repository.FinancialStatementRepository;
import com.bizkredit.credit.repository.CreditProposalRepository;
import com.bizkredit.credit.repository.UnderwritingDecisionRepository;
import com.bizkredit.credit.exception.BadRequestException;
import com.bizkredit.credit.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialAnalysisService {

    private final FinancialStatementRepository statementRepository;
    private final CreditProposalRepository proposalRepository;
    private final UnderwritingDecisionRepository decisionRepository;
    private final SmeGateway smeGateway;
    private final AuditLogService auditLogService;
    private final NotificationHelper notificationHelper;
    private final ScorecardService scorecardService;
    private final MakerCheckerService makerCheckerService;

    // Surfaces the previously-silent "could not advance application status in
    // sme-loan-service" failure from submitProposal()/makeDecision() back to
    // the controller, so it can be shown to the user instead of only ever
    // appearing in this service's own logs. The proposal/decision write
    // itself must still succeed even when this downstream call fails (that's
    // why it's a warning surfaced after the fact, not an exception thrown
    // from the transactional method itself) - but a failure here must no
    // longer be invisible to the person who took the action, since it means
    // the application is now stuck and needs manual reconciliation.
    private final ThreadLocal<String> lastStatusAdvanceWarning = new ThreadLocal<>();

    /** Call once, immediately after submitProposal()/makeDecision(), to check
     *  whether the downstream application-status advance actually succeeded. */
    public String consumeStatusAdvanceWarning() {
        String w = lastStatusAdvanceWarning.get();
        lastStatusAdvanceWarning.remove();
        return w;
    }


    @Transactional
    public FinancialStatement addStatement(Long applicationId, FinancialStatement statement) {
        // Validates the application exists in sme-loan-service (over Feign) before
        // attaching a statement to it. Throws ResourceNotFoundException if missing -
        // same contract as the old local repository lookup, just over HTTP now.
        smeGateway.getApplication(applicationId);
        statement.setApplicationId(applicationId);
        statement = computeRatios(statement);
        FinancialStatement saved = statementRepository.save(statement);
        auditLogService.log(null, "CREATE", "FinancialStatement", String.valueOf(saved.getStatementId()));
        log.info("Financial statement added for application {}, year {}", applicationId, saved.getFinancialYear());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<FinancialStatement> getStatementsByApplication(Long applicationId) {
        return statementRepository.findByApplicationId(applicationId);
    }

    @Transactional
    public FinancialStatement verifyStatement(Long statementId) {
        FinancialStatement statement = statementRepository.findById(statementId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found: " + statementId));
        statement.setStatus("Verified");
        auditLogService.log(null, "STATUS_CHANGE", "FinancialStatement", String.valueOf(statementId));
        log.info("Statement {} verified", statementId);
        return statementRepository.save(statement);
    }

    @Transactional
    public CreditProposal createProposal(Long applicationId, CreditProposal proposal) {
        smeGateway.getApplication(applicationId); // validates existence, same as addStatement
        proposal.setApplicationId(applicationId);
        proposal.setStatus(ProposalStatus.DRAFT);
        applyAutoScorecard(proposal, applicationId);
        CreditProposal saved = proposalRepository.save(proposal);
        auditLogService.log(null, "CREATE", "CreditProposal", String.valueOf(saved.getProposalId()));
        log.info("Credit proposal created for application {}", applicationId);
        return saved;
    }

    /**
     * Looks up the active scorecard for the application's product type and runs it,
     * populating computedRatingScore/riskCategory/computedScore/scorecardId on the proposal.
     * No-op (leaves fields as the analyst left them) if no ACTIVE scorecard exists yet
     * for the product, so proposal creation is never blocked on scorecard setup.
     *
     * Fetches the application from sme-loan-service over Feign (via SmeGateway) - any
     * failure here (not found, or sme-loan-service unreachable) is logged and swallowed,
     * same as a scoring failure: it must never block proposal creation/submission.
     */
    private void applyAutoScorecard(CreditProposal proposal, Long applicationId) {
        LoanApplicationDTO application;
        try {
            application = smeGateway.getApplication(applicationId);
        } catch (Exception e) {
            log.error("Could not fetch application {} for auto-scoring: {}", applicationId, e.getMessage());
            return;
        }

        ProductType productType;
        try {
            productType = ProductType.valueOf(application.getProductType());
        } catch (Exception e) {
            log.warn("Unknown productType '{}' for application {} - skipping auto-score",
                    application.getProductType(), applicationId);
            return;
        }

        var scorecard = scorecardService.findActiveScorecardFor(productType);
        if (scorecard == null) {
            log.info("No ACTIVE scorecard for product type {} - skipping auto-score for application {}",
                    productType, applicationId);
            return;
        }
        try {
            var result = scorecardService.computeForApplication(scorecard, applicationId);
            proposal.setScorecardId(scorecard.getScorecardId());
            proposal.setComputedScore(result.computedScore());
            proposal.setRatingLabel(result.rating());
            if (result.rating() != null) {
                proposal.setComputedRatingScore(new BigDecimal(result.computedScore()));
            }
            proposal.setRiskCategory(result.riskCategory());
            proposal.setScorecardAutoComputed(true);
            if (result.partialData()) {
                log.warn("Auto-score for application {} used partial data (weight applied: {})",
                        applicationId, result.totalWeightApplied());
            }
        } catch (Exception e) {
            // Never let a scoring failure block proposal creation/submission - log and move on,
            // the analyst can still set the rating manually.
            log.error("Auto-scoring failed for application {}: {}", applicationId, e.getMessage());
        }
    }

    // PUT /api/financial/proposals/{id} - update (Draft only)
    @Transactional
    public CreditProposal updateProposal(Long proposalId, CreditProposal updates) {
        CreditProposal existing = getProposalById(proposalId);
        if (existing.getStatus() != ProposalStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT proposals can be updated");
        }
        if (updates.getRatingLabel() != null) {
            existing.setRatingLabel(updates.getRatingLabel());
            // An explicit manual rating means the analyst is overriding the auto-score -
            // don't let a later submit() silently recompute and replace it.
            existing.setScorecardAutoComputed(false);
        }
        if (updates.getRiskCategory() != null) {
            existing.setRiskCategory(updates.getRiskCategory());
            existing.setScorecardAutoComputed(false);
        }
        if (updates.getSuggestedAmount() != null) existing.setSuggestedAmount(updates.getSuggestedAmount());
        if (updates.getSuggestedRate() != null) existing.setSuggestedRate(updates.getSuggestedRate());
        if (updates.getTenure() != null) existing.setTenure(updates.getTenure());
        if (updates.getConditions() != null) existing.setConditions(updates.getConditions());
        if (updates.getAnalystRecommendation() != null) existing.setAnalystRecommendation(updates.getAnalystRecommendation());
        auditLogService.log(null, "UPDATE", "CreditProposal", String.valueOf(proposalId));
        return proposalRepository.save(existing);
    }

    @Transactional
    public CreditProposal submitProposal(Long proposalId) {
        CreditProposal proposal = getProposalById(proposalId);
        String message = switch (proposal.getStatus()) {
            case DRAFT -> null;
            case SUBMITTED -> "Proposal already submitted";
            case APPROVED_BY_MANAGER -> "Proposal already approved";
            case DECLINED -> "Proposal was declined";
            case SANCTIONED -> "Proposal already sanctioned";
        };
        if (message != null) throw new BadRequestException(message);

        // Re-score on submit (only if the analyst hasn't manually overridden the rating)
        // so the submitted proposal reflects the latest financial statement / KYC data,
        // not whatever was on hand at creation time.
        if (Boolean.TRUE.equals(proposal.getScorecardAutoComputed()) && proposal.getApplicationId() != null) {
            applyAutoScorecard(proposal, proposal.getApplicationId());
        }

        proposal.setStatus(ProposalStatus.SUBMITTED);
        auditLogService.log(null, "STATUS_CHANGE", "CreditProposal", String.valueOf(proposalId));
        log.info("Proposal {} submitted for underwriting", proposalId);
        CreditProposal saved = proposalRepository.save(proposal);

        // Advance the application itself to UNDERWRITING_APPROVAL so it shows up
        // on the Underwriting Manager's queue. Without this, the application stays
        // on IN_REVIEW forever - the proposal is SUBMITTED but nothing ever moves
        // the application into the underwriting manager's filter, so their
        // dashboard (which queries by application status) always shows zero.
        // Same swallow-on-failure pattern as makeDecision(): the proposal submit
        // itself must not be rolled back if sme-loan-service is unreachable.
        if (proposal.getApplicationId() != null) {
            try {
                // sme-loan-service's own status-update endpoint already notifies
                // the UNDERWRITING_MANAGER role whenever an application reaches
                // this status, so no separate notification call is needed here.
                smeGateway.updateApplicationStatus(proposal.getApplicationId(), ApplicationStatus.UNDERWRITING_APPROVAL.name());
            } catch (Exception e) {
                log.error("Could not advance application {} to UNDERWRITING_APPROVAL",
                        proposal.getApplicationId(), e);
                lastStatusAdvanceWarning.set("Proposal submitted, but application #" + proposal.getApplicationId()
                        + " could not be advanced to UNDERWRITING_APPROVAL (" + e.getClass().getSimpleName()
                        + (e.getMessage() != null ? ": " + e.getMessage() : "")
                        + "). It will not appear in the Underwriting Manager's queue until this is reconciled.");
            }
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public CreditProposal getProposalById(Long proposalId) {
        return proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException("Proposal not found: " + proposalId));
    }

    @Transactional(readOnly = true)
    public List<CreditProposal> getProposalsByStatus(ProposalStatus status) {
        return proposalRepository.findByStatus(status);
    }

    @Transactional
    public UnderwritingDecision makeDecision(Long proposalId, UnderwritingDecision decision) {
        CreditProposal proposal = getProposalById(proposalId);

        if (proposal.getStatus() != ProposalStatus.SUBMITTED) {
            throw new BadRequestException("Proposal must be SUBMITTED before a decision can be made");
        }

        // Validate SanctionedAmount <= SuggestedAmount
        if (decision.getSanctionedAmount() != null && proposal.getSuggestedAmount() != null) {
            if (decision.getSanctionedAmount().compareTo(proposal.getSuggestedAmount()) > 0) {
                throw new BadRequestException("Sanctioned amount cannot exceed suggested amount of "
                        + proposal.getSuggestedAmount());
            }
        }

        decision.setProposal(proposal);
        // Force today's date regardless of what (if anything) the request
        // body sent for this field. The entity's own field initializer
        // (`= LocalDate.now()`) only applies to a freshly-constructed empty
        // object - if the incoming JSON explicitly included a null/absent
        // decisionDate, Jackson's setter call during @RequestBody binding
        // can still clear it back to null, which is exactly why this was
        // showing as a blank "—" in the Sanction Decisions table.
        decision.setDecisionDate(java.time.LocalDate.now());

        ProposalStatus newProposalStatus = switch (decision.getStatus()) {
            case APPROVED, CONDITIONAL_APPROVAL -> ProposalStatus.APPROVED_BY_MANAGER;
            case DECLINED -> ProposalStatus.DECLINED;
        };

        proposal.setStatus(newProposalStatus);
        proposalRepository.save(proposal);

        // Resolves the SUBMIT_PROPOSAL ledger entry as a side effect of THIS
        // decision, rather than making the manager separately click approve
        // on the ledger too - see MakerCheckerService.autoResolveForDecision
        // for why. Best-effort: a failure here must never block the real
        // decision, which is already saved above.
        try {
            makerCheckerService.autoResolveForDecision(
                    proposalId,
                    org.springframework.security.core.context.SecurityContextHolder.getContext()
                            .getAuthentication().getName(),
                    decision.getStatus() == DecisionStatus.DECLINED
                            ? com.bizkredit.credit.enums.MakerCheckerStatus.REJECTED
                            : com.bizkredit.credit.enums.MakerCheckerStatus.APPROVED);
        } catch (Exception e) {
            log.warn("Could not auto-resolve maker-checker ledger for proposal {}: {}", proposalId, e.getMessage());
        }

        // Auto-transition the application on decision. This whole block talks to
        // sme-loan-service over Feign (SmeGateway) - if it's unreachable, the decision
        // itself (already saved above) must not be rolled back, so failures here are
        // logged and swallowed rather than thrown; the application-side transition can
        // be reconciled separately if it doesn't go through.
        try {
            LoanApplicationDTO application = smeGateway.getApplication(proposal.getApplicationId());
            ApplicationStatus newAppStatus = switch (decision.getStatus()) {
                case APPROVED, CONDITIONAL_APPROVAL -> ApplicationStatus.SANCTIONED;
                case DECLINED -> ApplicationStatus.REJECTED;
            };
            // The application row is owned by sme-loan-service - write the status
            // there via Feign instead of directly into bizkredit_sme_db.loan_application.
            smeGateway.updateApplicationStatus(application.getApplicationId(), newAppStatus.name());

            // Notify assigned analyst of the decision
            if (application.getAssignedAnalystId() != null) {
                notificationHelper.notify(application.getAssignedAnalystId(),
                        "Application #" + application.getApplicationId() + " has been "
                                + newAppStatus.name() + " by underwriting",
                        NotificationCategory.APPLICATION);
            }
            // Notify the applicant directly - this is the decision they've
            // been waiting for on their own loan.
            if (application.getApplicantUserId() != null) {
                notificationHelper.notify(application.getApplicantUserId(),
                        "A decision has been made on your application #" + application.getApplicationId()
                                + ": " + decision.getStatus().name().replace('_', ' '),
                        NotificationCategory.APPLICATION);
            }
            // On approval, the next steps are collateral evaluation then
            // facility setup - notify those roles (they have no assigned
            // user, so broadcast to all holders of each role).
            if (newAppStatus == ApplicationStatus.SANCTIONED) {
                notificationHelper.notifyRole("COLLATERAL_EVALUATOR",
                        "Application #" + application.getApplicationId() + " is approved - collateral can now be registered",
                        NotificationCategory.APPLICATION);
                notificationHelper.notifyRole("RELATIONSHIP_MANAGER",
                        "Application #" + application.getApplicationId() + " is approved and heading for facility setup",
                        NotificationCategory.APPLICATION);
            }
        } catch (Exception e) {
            // Logging the full exception (not just e.getMessage(), which can be
            // blank/unhelpful for things like NullPointerException) since the
            // message-only version of this log has repeatedly failed to show
            // the actual cause of this call not going through.
            log.error("Could not complete application-side transition for application {} (proposal {})",
                    proposal.getApplicationId(), proposalId, e);
            lastStatusAdvanceWarning.set("Decision recorded, but application #" + proposal.getApplicationId()
                    + " could not be advanced/notified (" + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : "")
                    + "). Its status is still whatever it was before this decision - check sme-loan-service"
                    + " connectivity and reconcile the application's status manually if needed.");
        }

        UnderwritingDecision saved = decisionRepository.save(decision);
        auditLogService.log(null, "APPROVE", "UnderwritingDecision", String.valueOf(saved.getDecisionId()));
        log.info("Underwriting decision {} for proposal {}", decision.getStatus(), proposalId);
        return saved;
    }

    @Transactional(readOnly = true)
    public UnderwritingDecision getDecisionByProposal(Long proposalId) {
        return decisionRepository.findByProposal_ProposalId(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Decision not found for proposal: " + proposalId));
    }

    // GET statement by ID
    @Transactional(readOnly = true)
    public FinancialStatement getStatementById(Long id) {
        return statementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found: " + id));
    }

    // UPDATE statement (Draft only)
    @Transactional
    public FinancialStatement updateStatement(Long id, FinancialStatement updates) {
        FinancialStatement existing = statementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Statement not found: " + id));
        if (updates.getRevenue() != null) existing.setRevenue(updates.getRevenue());
        if (updates.getEbitda() != null) existing.setEbitda(updates.getEbitda());
        if (updates.getPat() != null) existing.setPat(updates.getPat());
        if (updates.getTotalAssets() != null) existing.setTotalAssets(updates.getTotalAssets());
        if (updates.getTotalLiabilities() != null) existing.setTotalLiabilities(updates.getTotalLiabilities());
        // Recompute
        if (existing.getTotalAssets() != null && existing.getTotalLiabilities() != null) {
            existing.setNetWorth(existing.getTotalAssets().subtract(existing.getTotalLiabilities()));
        }
        return statementRepository.save(computeRatios(existing));
    }

    // GET proposals by application
    @Transactional(readOnly = true)
    public List<CreditProposal> getProposalsByApplication(Long applicationId) {
        return proposalRepository.findAllByApplicationId(applicationId);
    }

    private FinancialStatement computeRatios(FinancialStatement s) {
        try {
            if (s.getTotalAssets() != null && s.getTotalLiabilities() != null
                    && s.getTotalLiabilities().compareTo(BigDecimal.ZERO) != 0) {
                s.setCurrentRatio(s.getTotalAssets()
                        .divide(s.getTotalLiabilities(), 2, RoundingMode.HALF_UP));
            }
            if (s.getTotalLiabilities() != null && s.getNetWorth() != null
                    && s.getNetWorth().compareTo(BigDecimal.ZERO) != 0) {
                s.setDebtEquityRatio(s.getTotalLiabilities()
                        .divide(s.getNetWorth(), 2, RoundingMode.HALF_UP));
            }
            if (s.getEbitda() != null && s.getTotalLiabilities() != null
                    && s.getTotalLiabilities().compareTo(BigDecimal.ZERO) != 0) {
                s.setDscr(s.getEbitda()
                        .divide(s.getTotalLiabilities(), 2, RoundingMode.HALF_UP));
            }
        } catch (Exception e) {
            log.warn("Could not compute some ratios: {}", e.getMessage());
        }
        return s;
    }
}
