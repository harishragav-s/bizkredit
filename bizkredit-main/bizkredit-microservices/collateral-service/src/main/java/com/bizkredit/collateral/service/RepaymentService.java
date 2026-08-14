package com.bizkredit.collateral.service;

import com.bizkredit.collateral.client.SmeLoanGateway;
import com.bizkredit.collateral.entity.Drawdown;
import com.bizkredit.collateral.entity.FacilityAccount;
import com.bizkredit.collateral.entity.Repayment;
import com.bizkredit.collateral.enums.DrawdownStatus;
import com.bizkredit.collateral.enums.RepaymentStatus;
import com.bizkredit.collateral.exception.BadRequestException;
import com.bizkredit.collateral.exception.ResourceNotFoundException;
import com.bizkredit.collateral.repository.DrawdownRepository;
import com.bizkredit.collateral.repository.FacilityAccountRepository;
import com.bizkredit.collateral.repository.RepaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RepaymentService {

    private final RepaymentRepository repaymentRepository;
    private final DrawdownRepository drawdownRepository;
    private final FacilityAccountRepository facilityRepository;
    private final SmeLoanGateway smeLoanGateway;
    private final NotificationHelper notificationHelper;

    /**
     * Records a repayment claim. What happens next depends on WHO is
     * recording it:
     * - RELATIONSHIP_MANAGER/ADMIN: staff recording it IS the
     *   confirmation (they're looking at the bank statement, etc.) -
     *   applied to the facility balance immediately, exactly as before.
     * - SME_APPLICANT: this is only a claim that a payment was made.
     *   The balance is NOT touched here - it only moves once an RM
     *   calls verifyRepayment(). Without this split, an applicant
     *   submitting a repayment would reduce their own outstanding
     *   balance with zero bank confirmation that the money ever arrived.
     */
    @Transactional
    public Repayment recordRepayment(Long drawdownId, Repayment repayment) {
        Drawdown drawdown = drawdownRepository.findById(drawdownId)
                .orElseThrow(() -> new ResourceNotFoundException("Drawdown not found"));

        FacilityAccount facility = drawdown.getFacility();

        if (repayment.getPrincipalComponent() != null && repayment.getInterestComponent() != null) {
            BigDecimal total = repayment.getPrincipalComponent().add(repayment.getInterestComponent());

            if (total.compareTo(repayment.getAmount()) != 0) {
                throw new BadRequestException("Principal + Interest must equal total amount");
            }
        }

        BigDecimal alreadyRepaid = repaymentRepository.sumRepaidForDrawdown(drawdown.getDrawdownId());
        BigDecimal remaining = drawdown.getAmount().subtract(alreadyRepaid);

        if (repayment.getAmount().compareTo(remaining) > 0) {
            throw new BadRequestException("Repayment amount exceeds outstanding amount");
        }

        repayment.setDrawdown(drawdown);
        repayment.setFacility(facility);

        boolean submittedByApplicant = currentUserHasRole("SME_APPLICANT") && !currentUserHasRole("RELATIONSHIP_MANAGER") && !currentUserHasRole("ADMIN");

        if (submittedByApplicant) {
            // Just a claim - not applied to the balance until an RM verifies it.
            repayment.setStatus(RepaymentStatus.PENDING_VERIFICATION);
            Repayment saved = repaymentRepository.save(repayment);

            notificationHelper.notifyRole("RELATIONSHIP_MANAGER",
                    "\u20B9" + repayment.getAmount() + " repayment claimed by applicant on facility #"
                            + facility.getFacilityId() + " - awaiting verification",
                    "FACILITY");

            return saved;
        }

        // Staff (RM/ADMIN) recording it directly IS the confirmation -
        // applied to the balance immediately, same as this always worked.
        repayment.setStatus(RepaymentStatus.RECEIVED);

        facility.setOutstandingBalance(
                facility.getOutstandingBalance().subtract(repayment.getAmount())
        );
        facilityRepository.save(facility);

        Repayment saved = repaymentRepository.save(repayment);

        boolean fullyRepaid = applyFullyRepaidIfNeeded(drawdown);

        notifyRepaymentConfirmed(facility, drawdown, repayment.getAmount(), fullyRepaid);

        return saved;
    }

    private boolean currentUserHasRole(String role) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    /** Flips the drawdown to REPAID if CONFIRMED repayments now cover it. Returns whether it did. */
    private boolean applyFullyRepaidIfNeeded(Drawdown drawdown) {
        BigDecimal confirmed = repaymentRepository.sumConfirmedForDrawdown(drawdown.getDrawdownId());
        boolean fullyRepaid = confirmed.compareTo(drawdown.getAmount()) >= 0;
        if (fullyRepaid && drawdown.getStatus() != DrawdownStatus.REPAID) {
            drawdown.setStatus(DrawdownStatus.REPAID);
            drawdownRepository.save(drawdown);
        }
        return fullyRepaid;
    }

    // This was missing entirely - RepaymentService had no notification
    // wiring at all, so neither the applicant nor the RM ever heard
    // anything when a repayment was recorded. Best-effort: a
    // notification failure must never undo a repayment that's already
    // been applied to the outstanding balance above.
    private void notifyRepaymentConfirmed(FacilityAccount facility, Drawdown drawdown, BigDecimal amount, boolean fullyRepaid) {
        try {
            var application = smeLoanGateway.getApplication(facility.getApplicationId());
            String suffix = fullyRepaid ? " - drawdown #" + drawdown.getDrawdownId() + " is now fully repaid." : ".";
            if (application != null && application.getApplicantUserId() != null) {
                notificationHelper.notify(application.getApplicantUserId(),
                        "\u20B9" + amount + " repayment confirmed on facility #" + facility.getFacilityId() + suffix,
                        "FACILITY");
            }
            notificationHelper.notifyRole("RELATIONSHIP_MANAGER",
                    "\u20B9" + amount + " repayment recorded on facility #" + facility.getFacilityId() + suffix,
                    "FACILITY");
        } catch (Exception e) {
            // Deliberately no log.error/rethrow here beyond this catch -
            // RepaymentService doesn't have a logger wired up, and adding
            // one just for a best-effort notification path isn't worth the
            // extra field; NotificationHelper already logs failures itself.
        }
    }

    @Transactional(readOnly = true)
    public List<Repayment> getByFacility(Long facilityId) {
        return repaymentRepository.findByFacility_FacilityId(facilityId);
    }

    @Transactional(readOnly = true)
    public List<Repayment> getByDrawdown(Long drawdownId) {
        return repaymentRepository.findByDrawdown_DrawdownId(drawdownId);
    }

    @Transactional(readOnly = true)
    public Repayment getById(Long repaymentId) {
        return repaymentRepository.findById(repaymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Repayment not found"));
    }

    @Transactional
    public Repayment verifyRepayment(Long repaymentId, Long verifiedById) {
        Repayment repayment = getById(repaymentId);

        if (repayment.getStatus() != RepaymentStatus.PENDING_VERIFICATION) {
            throw new BadRequestException(
                    "Only a repayment awaiting verification can be verified. Current status: " + repayment.getStatus());
        }
        if (repayment.getRecordedById() != null && repayment.getRecordedById().equals(verifiedById)) {
            throw new BadRequestException("Verifier cannot be same as recorder");
        }

        Drawdown drawdown = repayment.getDrawdown();
        FacilityAccount facility = repayment.getFacility();

        // This is the moment the claim actually becomes real money against
        // the balance - not when the applicant first submitted it.
        facility.setOutstandingBalance(facility.getOutstandingBalance().subtract(repayment.getAmount()));
        facilityRepository.save(facility);

        repayment.setStatus(RepaymentStatus.VERIFIED);
        repayment.setVerifiedById(verifiedById);
        Repayment saved = repaymentRepository.save(repayment);

        boolean fullyRepaid = applyFullyRepaidIfNeeded(drawdown);
        notifyRepaymentConfirmed(facility, drawdown, repayment.getAmount(), fullyRepaid);

        return saved;
    }
}
