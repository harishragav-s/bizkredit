package com.bizkredit.collateral.entity;

import com.bizkredit.collateral.enums.AssetType;
import com.bizkredit.collateral.enums.CollateralStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "collateral_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CollateralRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long collateralId;

    // Owned by sme-loan-service - id only, fetched over Feign (SmeLoanGateway)
    // when the applicant needs to be notified. Not @NotNull: set by the
    // service from the URL path param after @Valid has already run on the
    // incoming request body, which never includes it.
    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Asset type is required")
    private AssetType assetType;

    private String description;

    private String ownerName;

    @NotNull(message = "Market value is required")
    @Positive(message = "Market value must be positive")
    private BigDecimal marketValue;

    @Positive(message = "Force value percent must be positive")
    private BigDecimal forceValuePercent;

    private BigDecimal realisableValue;

    private LocalDate valuationDate;

    private Long valuedById;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CollateralStatus status = CollateralStatus.REGISTERED;

    // BP2-37 - Collateral Re-valuation Cycle Management. Configurable per
    // asset type (see CollateralFacilityService.defaultRevaluationFrequencyDays),
    // but stored per-record so an individual collateral's cycle can be
    // overridden without a code change.
    private Integer revaluationFrequencyDays;

    private LocalDate nextRevaluationDate;
}
