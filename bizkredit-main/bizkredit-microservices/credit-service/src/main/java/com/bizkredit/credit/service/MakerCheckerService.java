package com.bizkredit.credit.service;

import com.bizkredit.credit.dto.MakerCheckerDTOs.MakerCheckerRequest;
import com.bizkredit.credit.entity.MakerCheckerRecord;
import com.bizkredit.credit.enums.MakerCheckerStatus;
import com.bizkredit.credit.exception.ResourceNotFoundException;
import com.bizkredit.credit.repository.MakerCheckerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// BP2-17/18 - Maker-Checker for credit proposal submission and underwriting
// decisions. Mirrors collateral-service's MakerCheckerService exactly.
@Service
@RequiredArgsConstructor
@Slf4j
public class MakerCheckerService {

    private final MakerCheckerRepository repo;

    @Transactional
    public MakerCheckerRecord submit(MakerCheckerRequest request) {
        String maker = currentUsername();

        MakerCheckerRecord record = MakerCheckerRecord.builder()
                .entityType(request.entityType())
                .entityId(request.entityId())
                .action(request.action())
                .payloadJson(request.payloadJson())
                .submittedBy(maker)
                .requiredCheckerRole(request.requiredCheckerRole())
                .status(MakerCheckerStatus.PENDING_APPROVAL)
                .build();

        log.info("Maker-Checker: {} submitted {} on {} id={}",
                maker, request.action(), request.entityType(), request.entityId());

        return repo.save(record);
    }

    @Transactional(readOnly = true)
    public List<MakerCheckerRecord> getPendingForRole(String role) {
        return repo.findByRequiredCheckerRoleAndStatus(role, MakerCheckerStatus.PENDING_APPROVAL);
    }

    @Transactional
    public MakerCheckerRecord approve(Long id, String comments) {
        MakerCheckerRecord record = getOrThrow(id);
        validatePending(record);
        validateNotSelf(record);

        record.setStatus(MakerCheckerStatus.APPROVED);
        record.setCheckedBy(currentUsername());
        record.setCheckerComments(comments);

        log.info("Maker-Checker: {} APPROVED record id={}", currentUsername(), id);
        return repo.save(record);
    }

    /**
     * Auto-resolves the pending SUBMIT_PROPOSAL ledger entry for a proposal
     * the instant a real underwriting decision is recorded for it - so the
     * Underwriting Manager only has to take ONE action (Approve/Decline in
     * DecisionForm), not a second, separate "approve" click on the ledger
     * entry itself. The ledger is a record of what happened, not a second
     * gate the manager needs to operate by hand - previously it was
     * surfaced as its own approve/reject panel, which meant the same
     * decision-maker had to click Approve twice for one real event. If
     * nothing pending is found (e.g. it was submitted before maker-checker
     * logging existed), this is a no-op rather than an error.
     */
    @Transactional
    public void autoResolveForDecision(Long proposalId, String decidedBy, MakerCheckerStatus outcome) {
        repo.findByEntityTypeAndEntityId("CreditProposal", proposalId).stream()
                .filter(r -> r.getStatus() == MakerCheckerStatus.PENDING_APPROVAL)
                .forEach(r -> {
                    r.setStatus(outcome);
                    r.setCheckedBy(decidedBy);
                    r.setCheckerComments("Auto-resolved with the underwriting decision.");
                    repo.save(r);
                });
    }

    @Transactional
    public MakerCheckerRecord reject(Long id, String comments) {
        MakerCheckerRecord record = getOrThrow(id);
        validatePending(record);
        validateNotSelf(record);

        record.setStatus(MakerCheckerStatus.REJECTED);
        record.setCheckedBy(currentUsername());
        record.setCheckerComments(comments);

        log.info("Maker-Checker: {} REJECTED record id={}", currentUsername(), id);
        return repo.save(record);
    }

    private MakerCheckerRecord getOrThrow(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MakerCheckerRecord not found: " + id));
    }

    private void validatePending(MakerCheckerRecord record) {
        if (record.getStatus() != MakerCheckerStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                    "Record is not in PENDING_APPROVAL state. Current state: " + record.getStatus());
        }
    }

    private void validateNotSelf(MakerCheckerRecord record) {
        if (record.getSubmittedBy().equals(currentUsername())) {
            throw new IllegalStateException(
                    "Maker and checker cannot be the same user. Self-approval is not permitted.");
        }
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
