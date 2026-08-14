package com.bizkredit.credit.entity;

import com.bizkredit.credit.enums.MakerCheckerAction;
import com.bizkredit.credit.enums.MakerCheckerStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

// BP2-17/18 - dual authorization ledger for credit proposal submission and
// underwriting decisions. Mirrors collateral-service's MakerCheckerRecord
// (same shape, same enforcement level: a submission/approval ledger the
// frontend calls explicitly, not yet a hard gate inside
// FinancialAnalysisService.submitProposal()/makeDecision() themselves -
// consistent with how maker-checker is implemented everywhere else in this
// codebase today, rather than promising a stricter guarantee this service
// doesn't actually enforce).
@Entity
@Table(name = "maker_checker_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MakerCheckerRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String entityType;           // "CreditProposal" or "UnderwritingDecision"

    private Long entityId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MakerCheckerAction action;

    @Column(columnDefinition = "TEXT")
    private String payloadJson;

    @NotBlank
    @Column(nullable = false)
    private String submittedBy;

    @NotBlank
    @Column(nullable = false)
    private String requiredCheckerRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MakerCheckerStatus status = MakerCheckerStatus.PENDING_APPROVAL;

    private String checkedBy;

    @Column(columnDefinition = "TEXT")
    private String checkerComments;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
