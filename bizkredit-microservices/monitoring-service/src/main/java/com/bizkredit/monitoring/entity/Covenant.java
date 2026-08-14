package com.bizkredit.monitoring.entity;

import com.bizkredit.monitoring.enums.CovenantStatus;
import com.bizkredit.monitoring.enums.CovenantType;
import com.bizkredit.monitoring.enums.MonitoringFrequency;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "covenant")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Covenant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long covenantId;

    // Owned by collateral-service - id only, fetched over Feign
    // (CollateralGateway) wherever the full facility is needed. Not
    // @NotNull: set by the service from the URL path param after @Valid
    // has already run on the incoming request body, which never includes it.
    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Covenant type is required")
    private CovenantType covenantType;

    @NotBlank(message = "Description is required")
    private String description;

    private BigDecimal thresholdValue;

    @Enumerated(EnumType.STRING)
    private MonitoringFrequency monitoringFrequency;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CovenantStatus status = CovenantStatus.ACTIVE;

    // Needed as a baseline for automatic due-date checking (see
    // CovenantDueScheduler): before any tracking record exists yet, "when
    // is compliance first due" has to be computed from SOMETHING - this
    // was previously missing entirely, so there was no way to know a
    // brand-new covenant's first review was overdue without a human
    // remembering to check.
    @Builder.Default
    private java.time.LocalDate createdDate = java.time.LocalDate.now();

    // Which computed ratio (if any) this covenant maps to - lets
    // CovenantDueScheduler auto-evaluate FINANCIAL covenants directly
    // from the applicant's latest financial statement instead of only
    // reminding an RM to go check it themselves. NONE for non-financial
    // covenants and for financial covenants an admin hasn't mapped yet
    // (e.g. ones created before this existed) - those still fall back to
    // a reminder, same as before.
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private com.bizkredit.monitoring.enums.FinancialMetric financialMetric =
            com.bizkredit.monitoring.enums.FinancialMetric.NONE;
}
