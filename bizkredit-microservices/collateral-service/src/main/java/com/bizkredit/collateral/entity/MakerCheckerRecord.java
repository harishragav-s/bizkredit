package com.bizkredit.collateral.entity;

import com.bizkredit.collateral.enums.MakerCheckerAction;
import com.bizkredit.collateral.enums.MakerCheckerStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

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
    private String entityType;           // e.g. "LoanApplication", "FacilityAccount"

    private Long entityId;               // ID of the record being acted on (null for CREATE)

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MakerCheckerAction action;

    @Column(columnDefinition = "TEXT")
    private String payloadJson;          // JSON snapshot of the request payload

    @NotBlank
    @Column(nullable = false)
    private String submittedBy;          // username of the maker

    @NotBlank
    @Column(nullable = false)
    private String requiredCheckerRole;  // role that must approve, e.g. "UNDERWRITING_MANAGER"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MakerCheckerStatus status = MakerCheckerStatus.PENDING_APPROVAL;

    private String checkedBy;            // username of the checker who acted

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
