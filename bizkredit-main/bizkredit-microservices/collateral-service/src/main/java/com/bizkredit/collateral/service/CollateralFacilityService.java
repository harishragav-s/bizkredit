package com.bizkredit.collateral.service;

import com.bizkredit.collateral.client.CreditGateway;
import com.bizkredit.collateral.client.SmeLoanGateway;
import com.bizkredit.collateral.dto.LoanApplicationDTO;
import com.bizkredit.collateral.dto.UnderwritingDecisionDTO;
import com.bizkredit.collateral.entity.FacilityAccount;
import com.bizkredit.collateral.entity.CollateralRecord;
import com.bizkredit.collateral.entity.CollateralRevaluation;
import com.bizkredit.collateral.entity.Drawdown;
import com.bizkredit.collateral.entity.WorkingCapitalUtilisation;
import com.bizkredit.collateral.enums.CollateralStatus;
import com.bizkredit.collateral.repository.FacilityAccountRepository;
import com.bizkredit.collateral.repository.CollateralRecordRepository;
import com.bizkredit.collateral.repository.CollateralRevaluationRepository;
import com.bizkredit.collateral.repository.DrawdownRepository;
import com.bizkredit.collateral.repository.LoanApplicationRepository;
import com.bizkredit.collateral.repository.WorkingCapitalUtilisationRepository;
import com.bizkredit.collateral.enums.DrawdownStatus;
import com.bizkredit.collateral.enums.FacilityStatus;
import com.bizkredit.collateral.exception.BadRequestException;
import com.bizkredit.collateral.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollateralFacilityService {

    private final CollateralRecordRepository collateralRepository;
    private final CollateralRevaluationRepository revaluationRepository;
    private final FacilityAccountRepository facilityRepository;
    private final DrawdownRepository drawdownRepository;
    private final WorkingCapitalUtilisationRepository utilisationRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final SmeLoanGateway smeLoanGateway;
    private final CreditGateway creditGateway;
    private final NotificationHelper notificationHelper;

    // BP2-45/54 - Facility Renewal Management API

    @Transactional(readOnly = true)
    public List<FacilityAccount> getExpiringFacilities(int withinDays) {
        if (withinDays <= 0) {
            throw new BadRequestException("withinDays must be positive");
        }
        LocalDate now = LocalDate.now();
        return facilityRepository.findExpiringFacilities(now, now.plusDays(withinDays));
    }

    @Transactional
    public com.bizkredit.collateral.dto.LoanApplicationDTO renewFacility(Long facilityId) {
        FacilityAccount facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found: " + facilityId));

        if (facility.getStatus() != FacilityStatus.ACTIVE) {
            throw new BadRequestException("Only an Active facility can be renewed (current status: "
                    + facility.getStatus() + ")");
        }

        // Pre-fill BusinessID, ProductType, and RequestedAmount (= current
        // SanctionedLimit) from the expiring facility, per BP2-45 AC #2.
        // Carries a reference to the original FacilityID (renewedFromFacilityId)
        // for traceability, per AC #3.
        java.util.Map<String, Object> renewalRequest = new java.util.HashMap<>();
        renewalRequest.put("productType", facility.getProductType().name());
        renewalRequest.put("requestedAmount", facility.getSanctionedLimit());
        renewalRequest.put("tenure", 12); // default tenure for a renewal; RM adjusts before submission
        renewalRequest.put("purpose", "Renewal of Facility #" + facilityId);
        renewalRequest.put("renewedFromFacilityId", facilityId);

        com.bizkredit.collateral.dto.LoanApplicationDTO created =
                smeLoanGateway.createApplication(facility.getBusinessId(), renewalRequest);

        notificationHelper.notifyRole("RELATIONSHIP_MANAGER",
                "Renewal application #" + created.getApplicationId() + " initiated for expiring Facility #" + facilityId,
                "Facility");

        log.info("Renewal application {} initiated for facility {}", created.getApplicationId(), facilityId);
        return created;
    }

    @Transactional(readOnly = true)
    public List<com.bizkredit.collateral.entity.LoanApplication> getRenewalHistory(Long facilityId) {
        // Facility existence check keeps this consistent with the other
        // facility sub-resource endpoints (404 rather than a silent empty list
        // for a facility ID that doesn't exist at all).
        if (!facilityRepository.existsById(facilityId)) {
            throw new ResourceNotFoundException("Facility not found: " + facilityId);
        }
        return loanApplicationRepository.findByRenewedFromFacilityIdOrderByApplicationDateDesc(facilityId);
    }

    @Transactional
    public CollateralRecord registerCollateral(Long applicationId, CollateralRecord collateral) {
        LoanApplicationDTO application = smeLoanGateway.getApplication(applicationId);
        collateral.setApplicationId(applicationId);

        boolean disclosedByApplicant = currentUserHasRole("SME_APPLICANT")
                && !currentUserHasRole("COLLATERAL_EVALUATOR") && !currentUserHasRole("ADMIN");

        if (disclosedByApplicant) {
            // Just a declaration - description and an estimated value, not
            // yet counted toward coverage. setRealisableValue/revaluation-
            // cycle setup happens at evaluateCollateral() instead, since an
            // applicant's own guess at market value isn't a valuation.
            collateral.setStatus(CollateralStatus.DISCLOSED);
            CollateralRecord saved = collateralRepository.save(collateral);

            notificationHelper.notifyRole("COLLATERAL_EVALUATOR",
                    "Applicant disclosed collateral (" + collateral.getAssetType() + ") on application #"
                            + applicationId + " - awaiting evaluation",
                    "COLLATERAL");
            return saved;
        }

        // Evaluator/Admin recording it directly IS the confirmation - same
        // as RM recording a repayment directly, or a decision recorded by
        // Underwriting. Counted immediately, exactly as this always worked.
        collateral.setStatus(CollateralStatus.REGISTERED);
        setRealisableValue(collateral);

        // BP2-37 - configurable re-valuation frequency per collateral type
        // (property: annual, receivables: monthly, inventory-like/other:
        // quarterly) unless the caller explicitly overrides it.
        if (collateral.getRevaluationFrequencyDays() == null) {
            collateral.setRevaluationFrequencyDays(defaultRevaluationFrequencyDays(collateral.getAssetType()));
        }
        LocalDate baseline = collateral.getValuationDate() != null ? collateral.getValuationDate() : LocalDate.now();
        collateral.setNextRevaluationDate(baseline.plusDays(collateral.getRevaluationFrequencyDays()));

        CollateralRecord saved = collateralRepository.save(collateral);

        // Let the applicant know collateral has been recorded against
        // their application.
        if (application.getApplicantUserId() != null) {
            notificationHelper.notify(application.getApplicantUserId(),
                    "Collateral has been registered against your application #" + applicationId,
                    "COLLATERAL");
        }


        notificationHelper.notifyRole("RELATIONSHIP_MANAGER",
                "Collateral (" + collateral.getAssetType() + ") registered for application #" + applicationId,
                "COLLATERAL");
        notificationHelper.notifyRole("CREDIT_ANALYST",
                "Collateral (" + collateral.getAssetType() + ") registered for application #" + applicationId,
                "COLLATERAL");

        return saved;
    }

    private boolean currentUserHasRole(String role) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }


    @Transactional
    public CollateralRecord evaluateCollateral(Long collateralId, BigDecimal confirmedMarketValue) {
        CollateralRecord collateral = getCollateralById(collateralId);

        if (collateral.getStatus() != CollateralStatus.DISCLOSED) {
            throw new BadRequestException(
                    "Only applicant-disclosed collateral can be evaluated. Current status: " + collateral.getStatus());
        }

        collateral.setMarketValue(confirmedMarketValue);
        collateral.setValuationDate(LocalDate.now());
        setRealisableValue(collateral);

        if (collateral.getRevaluationFrequencyDays() == null) {
            collateral.setRevaluationFrequencyDays(defaultRevaluationFrequencyDays(collateral.getAssetType()));
        }
        collateral.setNextRevaluationDate(LocalDate.now().plusDays(collateral.getRevaluationFrequencyDays()));
        collateral.setStatus(CollateralStatus.REGISTERED);

        CollateralRecord saved = collateralRepository.save(collateral);

        try {
            LoanApplicationDTO application = smeLoanGateway.getApplication(collateral.getApplicationId());
            if (application.getApplicantUserId() != null) {
                notificationHelper.notify(application.getApplicantUserId(),
                        "Your disclosed collateral (" + collateral.getAssetType() + ") has been evaluated and confirmed",
                        "COLLATERAL");
            }
            notificationHelper.notifyRole("RELATIONSHIP_MANAGER",
                    "Collateral #" + collateralId + " evaluated and confirmed for application #"
                            + collateral.getApplicationId(),
                    "COLLATERAL");
        } catch (Exception e) {
            log.warn("Could not notify after evaluating collateral {}: {}", collateralId, e.getMessage());
        }

        return saved;
    }

    // BP2-37 - default cadence per asset type when none is explicitly set.
    // Property revalues slowest (physically/legally stable), receivables
    // fastest (value swings with the underlying debtor book), everything
    // else quarterly as a reasonable middle ground.
    private int defaultRevaluationFrequencyDays(com.bizkredit.collateral.enums.AssetType assetType) {
        if (assetType == null) return 90;
        return switch (assetType) {
            case PROPERTY -> 365;
            case RECEIVABLES -> 30;
            case GOLD, SECURITIES -> 90;
            case PLANT, MACHINERY, FD -> 90;
        };
    }

    public CollateralRecord getCollateralById(Long collateralId) {
        return collateralRepository.findById(collateralId)
                .orElseThrow(() -> new ResourceNotFoundException("Collateral not found"));
    }

    // Lists every collateral record registered against an application -
    // the evaluator previously had no way to see what was already
    // registered, only to register something new or fetch one record
    // if they already knew its exact ID.
    public List<CollateralRecord> getCollateralByApplication(Long applicationId) {
        return collateralRepository.findByApplicationId(applicationId);
    }

    @Transactional
    public CollateralRecord updateCollateral(Long collateralId, CollateralRecord updates) {
        CollateralRecord collateral = getCollateralById(collateralId);

        if (updates.getDescription() != null) collateral.setDescription(updates.getDescription());
        if (updates.getOwnerName() != null) collateral.setOwnerName(updates.getOwnerName());
        if (updates.getMarketValue() != null) collateral.setMarketValue(updates.getMarketValue());
        if (updates.getForceValuePercent() != null) collateral.setForceValuePercent(updates.getForceValuePercent());

        setRealisableValue(collateral);
        return collateralRepository.save(collateral);
    }

    @Transactional
    public CollateralRevaluation revalueCollateral(Long collateralId, BigDecimal newValue, Long revaluedById) {
        CollateralRecord collateral = getCollateralById(collateralId);
        BigDecimal oldValue = collateral.getMarketValue();

        if (oldValue == null || oldValue.compareTo(BigDecimal.ZERO) == 0) {
            throw new BadRequestException("Invalid previous market value");
        }

        collateral.setMarketValue(newValue);
        setRealisableValue(collateral);

        // BP2-37 - reset the revaluation clock from today, using whatever
        // cadence is configured on this record (falls back to the asset
        // type default if somehow unset, e.g. for records created before
        // this field existed).
        int frequency = collateral.getRevaluationFrequencyDays() != null
                ? collateral.getRevaluationFrequencyDays()
                : defaultRevaluationFrequencyDays(collateral.getAssetType());
        collateral.setRevaluationFrequencyDays(frequency);
        collateral.setValuationDate(LocalDate.now());
        collateral.setNextRevaluationDate(LocalDate.now().plusDays(frequency));

        collateralRepository.save(collateral);

        CollateralRevaluation saved = revaluationRepository.save(CollateralRevaluation.builder()
                .collateral(collateral)
                .revaluationDate(LocalDate.now())
                .previousValue(oldValue)
                .newValue(newValue)
                .revaluedById(revaluedById)
                .changePercent(newValue.subtract(oldValue)
                        .divide(oldValue, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")))
                .build());

        // BP2-37 AC - alert when collateral value drops, or when the
        // facility's overall coverage ratio breaches its minimum
        // requirement as a result. Best-effort: a notification failure
        // must never roll back a revaluation that's already been recorded.
        try {
            if (newValue.compareTo(oldValue) < 0) {
                notifyCollateralDrop(collateral, oldValue, newValue);
            }
            checkCoverageForApplication(collateral.getApplicationId());
        } catch (Exception e) {
            log.warn("Could not evaluate post-revaluation alerts for collateral {}: {}", collateralId, e.getMessage());
        }

        return saved;
    }

    private static final BigDecimal MIN_COVERAGE_RATIO_PERCENT = new BigDecimal("100");

    private void notifyCollateralDrop(CollateralRecord collateral, BigDecimal oldValue, BigDecimal newValue) {
        notificationHelper.notifyRole("COLLATERAL_EVALUATOR",
                "Collateral #" + collateral.getCollateralId() + " (application #" + collateral.getApplicationId()
                        + ") dropped in value from " + oldValue + " to " + newValue + " on revaluation",
                "COLLATERAL");
    }

    /**
     * BP2-19/37 - total realisable value coverage against the facility's
     * sanctioned limit for an application. Alerts the RM/Collateral team if
     * coverage has fallen under 100%. Returns null (no alert, no ratio) if
     * the application has no facility yet - coverage is meaningless before
     * a facility exists to cover.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getCollateralCoverage(Long applicationId) {
        List<CollateralRecord> records = collateralRepository.findByApplicationId(applicationId);
        // DISCLOSED (applicant-declared, not yet evaluated) must not count
        // toward coverage - same reasoning as PENDING_VERIFICATION
        // repayments not touching the balance yet.
        BigDecimal totalRealisable = records.stream()
                .filter(c -> c.getStatus() == CollateralStatus.REGISTERED || c.getStatus() == CollateralStatus.CHARGED)
                .map(c -> c.getRealisableValue() != null ? c.getRealisableValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FacilityAccount> facilities = facilityRepository.findByApplicationId(applicationId);

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("totalRealisableValue", totalRealisable);

        if (facilities.isEmpty()) {
            result.put("coveragePercent", null);
            result.put("sanctionedLimit", null);
            return result;
        }

        BigDecimal sanctionedLimit = facilities.get(0).getSanctionedLimit();
        result.put("sanctionedLimit", sanctionedLimit);

        if (sanctionedLimit != null && sanctionedLimit.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal coveragePercent = totalRealisable.divide(sanctionedLimit, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
            result.put("coveragePercent", coveragePercent);
        } else {
            result.put("coveragePercent", null);
        }
        return result;
    }

    private void checkCoverageForApplication(Long applicationId) {
        var coverage = getCollateralCoverage(applicationId);
        Object coveragePercentObj = coverage.get("coveragePercent");
        if (coveragePercentObj instanceof BigDecimal coveragePercent
                && coveragePercent.compareTo(MIN_COVERAGE_RATIO_PERCENT) < 0) {
            notificationHelper.notifyRole("RELATIONSHIP_MANAGER",
                    "Collateral coverage for application #" + applicationId + " has fallen to "
                            + coveragePercent.setScale(1, RoundingMode.HALF_UP) + "% (below 100% minimum)",
                    "COLLATERAL");
        }
    }

    // BP2-37 - dashboard of upcoming/overdue revaluations.
    @Transactional(readOnly = true)
    public List<CollateralRecord> getDueForRevaluation(int withinDays) {
        if (withinDays <= 0) {
            throw new BadRequestException("withinDays must be positive");
        }
        return collateralRepository.findDueForRevaluation(LocalDate.now().plusDays(withinDays));
    }

    @Transactional
    public FacilityAccount createFacility(Long applicationId, Long businessId, FacilityAccount facility) {
        LoanApplicationDTO application = smeLoanGateway.getApplication(applicationId);
        smeLoanGateway.getBusiness(businessId); // validates the business exists

        // Enforces that the facility's sanctioned limit can never
        // exceed what an Underwriting Manager actually approved -
        // without this, an RM could create a facility for any amount
        // they typed, regardless of the real underwriting decision,
        // which made that entire approval step non-binding in
        // practice. Uses the most recent decision if the application
        // had more than one proposal over its life (e.g. after an
        // earlier decline).
        UnderwritingDecisionDTO latestDecision = creditGateway.getLatestDecisionForApplication(applicationId)
                .orElseThrow(() -> new BadRequestException(
                        "No underwriting decision found for this application - a facility cannot be created before underwriting has approved it"));

        BigDecimal approvedAmount = latestDecision.getSanctionedAmount();

        if (approvedAmount != null && facility.getSanctionedLimit() != null
                && facility.getSanctionedLimit().compareTo(approvedAmount) > 0) {
            throw new BadRequestException(
                    "Sanctioned limit (" + facility.getSanctionedLimit()
                            + ") cannot exceed the amount approved by underwriting ("
                            + approvedAmount + ")");
        }

        facility.setApplicationId(applicationId);
        facility.setBusinessId(businessId);
        facility.setDisbursedAmount(BigDecimal.ZERO);
        facility.setOutstandingBalance(BigDecimal.ZERO);
        FacilityAccount savedFacility = facilityRepository.save(facility);

        // Notify the applicant their facility is ready to draw down.
        if (application.getApplicantUserId() != null) {
            notificationHelper.notify(application.getApplicantUserId(),
                    "Your facility #" + savedFacility.getFacilityId() + " has been created and is ready for drawdown",
                    "FACILITY");
        }
        return savedFacility;
    }

    public FacilityAccount getFacilityById(Long facilityId) {
        return facilityRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found"));
    }

    // Hard-deletes a facility only if nothing has ever been disbursed
    // against it - a facility with any real disbursement history must
    // go through closeFacility() instead (which itself requires zero
    // outstanding balance first), never a hard delete, to preserve the
    // audit trail a real bank is required to keep.
    @Transactional
    public void deleteFacility(Long facilityId) {
        FacilityAccount facility = getFacilityById(facilityId);
        boolean hasDisbursement = drawdownRepository.findByFacility_FacilityId(facilityId)
                .stream()
                .anyMatch(d -> d.getStatus() != DrawdownStatus.REQUESTED);

        if (hasDisbursement) {
            throw new BadRequestException(
                    "This facility has disbursement history and cannot be deleted - close it instead");
        }

        drawdownRepository.deleteAll(drawdownRepository.findByFacility_FacilityId(facilityId));
        facilityRepository.delete(facility);
    }

    // Lets an applicant (or RM/Admin) look up facilities by business ID
    // rather than needing to already know a raw facilityId - the
    // applicant has no other way to discover which facility number
    // was created against their own sanctioned application.
    public List<FacilityAccount> getFacilitiesByBusiness(Long businessId) {
        return facilityRepository.findByBusinessId(businessId);
    }

    // Lets the RM browse every facility across all businesses, with
    // optional status filtering - this is what makes Covenant Tracker
    // and EWS Board usable in practice, since both require typing a
    // raw facilityId with no prior way to discover what that ID is.
    public List<FacilityAccount> getAllFacilities(FacilityStatus status) {
        return facilityRepository.findWithFilters(null, status, null);
    }

    @Transactional
    public FacilityAccount updateFacility(Long facilityId, FacilityAccount updates) {
        FacilityAccount facility = getFacilityById(facilityId);

        if (updates.getExpiryDate() != null) facility.setExpiryDate(updates.getExpiryDate());
        if (updates.getInterestRate() != null) facility.setInterestRate(updates.getInterestRate());

        return facilityRepository.save(facility);
    }

    // Closing (not deleting) a facility is the real-world equivalent
    // of "removing" it - a sanctioned/disbursed credit facility is a
    // regulated financial record and is never actually deleted, only
    // formally closed once fully repaid. Enforces that rule rather
    // than letting the status be flipped with money still outstanding.
    @Transactional
    public FacilityAccount closeFacility(Long facilityId) {
        FacilityAccount facility = getFacilityById(facilityId);

        if (facility.getOutstandingBalance() != null
                && facility.getOutstandingBalance().compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException(
                    "Cannot close facility with outstanding balance of " + facility.getOutstandingBalance()
                            + " - record the remaining repayment first");
        }

        facility.setStatus(FacilityStatus.CLOSED);
        return facilityRepository.save(facility);
    }

    @Transactional
    public Drawdown requestDrawdown(Long facilityId, Drawdown drawdown) {
        FacilityAccount facility = getFacilityById(facilityId);
        BigDecimal available = facility.getSanctionedLimit().subtract(facility.getDisbursedAmount());

        if (drawdown.getAmount().compareTo(available) > 0) {
            throw new BadRequestException("Drawdown amount exceeds available limit");
        }

        drawdown.setFacility(facility);
        drawdown.setStatus(DrawdownStatus.REQUESTED);
        Drawdown saved = drawdownRepository.save(drawdown);

        // This was missing entirely - an applicant could request funds and
        // nobody at the bank was ever told. The RM had to happen to open
        // the facility page and notice a REQUESTED row sitting there.
        // Best-effort: a notification failure must never lose the
        // drawdown request itself, which is already committed above.
        try {
            notificationHelper.notifyRole("RELATIONSHIP_MANAGER",
                    "\u20B9" + drawdown.getAmount() + " drawdown requested by the borrower on facility #"
                            + facility.getFacilityId() + " - awaiting your transfer",
                    "FACILITY");
        } catch (Exception e) {
            log.warn("Could not notify RM of drawdown request on facility {}: {}",
                    facility.getFacilityId(), e.getMessage());
        }

        return saved;
    }

    public Drawdown getDrawdownById(Long drawdownId) {
        return drawdownRepository.findById(drawdownId)
                .orElseThrow(() -> new ResourceNotFoundException("Drawdown not found"));
    }

    // Lists every drawdown against a facility, regardless of who
    // requested it (RM or applicant) or when - without this, the only
    // drawdowns visible on a facility were ones created in the exact
    // same browser session currently viewing it.
    public List<Drawdown> getDrawdownsByFacility(Long facilityId) {
        return drawdownRepository.findByFacility_FacilityId(facilityId);
    }

    @Transactional
    public Drawdown disburseDrawdown(Long drawdownId) {
        Drawdown drawdown = getDrawdownById(drawdownId);
        FacilityAccount facility = drawdown.getFacility();

        drawdown.setStatus(DrawdownStatus.DISBURSED);
        drawdown.setDisbursedDate(LocalDate.now());
        // Sets the repayment due date this drawdown is measured against
        // for overdue/EWS/NPA purposes - a standard 30-day cycle for a
        // working-capital-style drawdown. Without this, repaymentDate
        // stays null forever and the entire downstream monitoring
        // chain (Drawdown -> OVERDUE -> EWS signal -> NPA
        // classification) has nothing to check against, regardless of
        // how much time actually passes with no repayment.
        drawdown.setRepaymentDate(LocalDate.now().plusDays(30));

        // .setScale(2, HALF_UP) is deliberate: BigDecimal addition
        // preserves whatever scale/precision the operands came in with, so
        // a value that picked up excess decimal places somewhere upstream
        // (e.g. a rate calculation elsewhere in the pipeline) would
        // otherwise silently propagate here. Rounding every monetary write
        // to 2 decimal places is what actually prevents "Available to
        // Draw" from showing a nonsense residual like "₹3" once
        // disbursed genuinely equals sanctioned.
        facility.setDisbursedAmount(facility.getDisbursedAmount().add(drawdown.getAmount())
                .setScale(2, RoundingMode.HALF_UP));
        facility.setOutstandingBalance(facility.getOutstandingBalance().add(drawdown.getAmount())
                .setScale(2, RoundingMode.HALF_UP));

        facilityRepository.save(facility);
        Drawdown savedDrawdown = drawdownRepository.save(drawdown);

        // Update the loan application status to DISBURSED so the
        // applicant's tracker shows the correct pipeline step. Only
        // move forward - never overwrite a terminal status. The
        // application row is owned by sme-loan-service - fetched and
        // written over Feign (SmeLoanGateway) rather than a local
        // cross-schema read/write. Best-effort: a failure here must
        // not roll back the disbursement that was just committed above.
        try {
            LoanApplicationDTO application = smeLoanGateway.getApplication(facility.getApplicationId());
            if ("SANCTIONED".equals(application.getStatus())) {
                smeLoanGateway.updateApplicationStatus(facility.getApplicationId(), "DISBURSED");
            }
            if (application.getApplicantUserId() != null) {
                notificationHelper.notify(application.getApplicantUserId(),
                        "\u20B9" + drawdown.getAmount() + " has been disbursed on your facility #" + facility.getFacilityId(),
                        "FACILITY");
            }
        } catch (Exception e) {
            log.error("Could not complete application-side transition for application {} (facility {}): {}",
                    facility.getApplicationId(), facility.getFacilityId(), e.getMessage());
        }

        return savedDrawdown;
    }

    // Called over Feign by monitoring-service's NPA classification job -
    // it detects the overdue condition (repaymentDate passed, still
    // unpaid), collateral-service still owns the actual write to its own
    // drawdown table.
    @Transactional
    public Drawdown markDrawdownOverdue(Long drawdownId) {
        Drawdown drawdown = getDrawdownById(drawdownId);
        drawdown.setStatus(DrawdownStatus.OVERDUE);
        return drawdownRepository.save(drawdown);
    }

    @Transactional
    public WorkingCapitalUtilisation recordUtilisation(Long facilityId, WorkingCapitalUtilisation utilisation) {
        utilisation.setFacility(getFacilityById(facilityId));
        setUtilisationValues(utilisation);
        return utilisationRepository.save(utilisation);
    }

    public List<WorkingCapitalUtilisation> getUtilisationByFacility(Long facilityId) {
        return utilisationRepository.findByFacility_FacilityId(facilityId);
    }

    // Called over Feign by monitoring-service's NPA classification job -
    // it decides WHEN a facility should flip to/from NPA (based on overdue
    // drawdowns it tracks), collateral-service still owns the actual write
    // to its own facility_account table.
    @Transactional
    public FacilityAccount updateNpaStatus(Long facilityId, FacilityStatus status) {
        FacilityAccount facility = getFacilityById(facilityId);
        facility.setStatus(status);
        return facilityRepository.save(facility);
    }

    private void setRealisableValue(CollateralRecord collateral) {
        if (collateral.getMarketValue() != null && collateral.getForceValuePercent() != null) {
            collateral.setRealisableValue(
                    collateral.getMarketValue()
                            .multiply(collateral.getForceValuePercent())
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
        }
    }

    private void setUtilisationValues(WorkingCapitalUtilisation utilisation) {
        if (utilisation.getDrawingPower() != null && utilisation.getCurrentUtilisation() != null) {
            utilisation.setAvailableLimit(utilisation.getDrawingPower().subtract(utilisation.getCurrentUtilisation()));

            if (utilisation.getDrawingPower().compareTo(BigDecimal.ZERO) != 0) {
                utilisation.setUtilisationPercent(
                        utilisation.getCurrentUtilisation()
                                .divide(utilisation.getDrawingPower(), 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100")));
            }
        }
    }
}
