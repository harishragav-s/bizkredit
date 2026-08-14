package com.bizkredit.collateral.service;

import com.bizkredit.collateral.dto.MakerCheckerDTOs.MakerCheckerRequest;
import com.bizkredit.collateral.entity.MakerCheckerRecord;
import com.bizkredit.collateral.enums.MakerCheckerStatus;
import com.bizkredit.collateral.exception.ResourceNotFoundException;
import com.bizkredit.collateral.repository.MakerCheckerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

        log.info(
                "Maker-Checker: {} submitted {} on {} id={}",
                maker,
                request.action(),
                request.entityType(),
                request.entityId()
        );

        return repo.save(record);
    }

    @Transactional(readOnly = true)
    public List<MakerCheckerRecord> getPendingForRole(String role) {
        return repo.findByRequiredCheckerRoleAndStatus(
                role,
                MakerCheckerStatus.PENDING_APPROVAL
        );
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
                    "Record is not in PENDING_APPROVAL state. Current state: " + record.getStatus()
            );
        }
    }

    private void validateNotSelf(MakerCheckerRecord record) {
        if (record.getSubmittedBy().equals(currentUsername())) {
            throw new IllegalStateException(
                    "Maker and checker cannot be the same user. Self-approval is not permitted."
            );
        }
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
