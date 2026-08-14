package com.bizkredit.monitoring.entity;

import com.bizkredit.monitoring.enums.ComplianceStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

// BP2-23 - periodic compliance record for a Covenant: an actual value
// reported for a period, compared against the covenant's threshold at the
// time of review. This was entirely missing from the codebase - Covenant
// itself only ever carried a single current status (ACTIVE/WAIVED/BREACHED)
// with no history of period-by-period actuals.
@Entity
@Table(name = "covenant_tracking")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CovenantTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long trackingId;

    @Column(name = "covenant_id", nullable = false)
    private Long covenantId;

    @NotBlank(message = "Period is required")
    private String period;

    private BigDecimal actualValue;

    // Copied from Covenant.thresholdValue at the time of review, per spec -
    // so later threshold edits on the covenant don't retroactively change
    // what a past period was actually judged against.
    private BigDecimal thresholdValue;

    @Enumerated(EnumType.STRING)
    @NotNull
    private ComplianceStatus complianceStatus;

    private Long reviewedById;

    @Builder.Default
    private LocalDate reviewDate = LocalDate.now();
}
