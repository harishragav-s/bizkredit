package com.bizkredit.monitoring.entity;

import com.bizkredit.monitoring.enums.FacilityStatus;
import com.bizkredit.monitoring.enums.ProductType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;

// Local mapping of the facility_account table, owned by collateral-service.
// monitoring-service both reads this (to attach covenants/EWS/NPA records)
// and writes to it (NPAClassificationService flips status to NPA/ACTIVE as
// part of the classification and upgrade workflow) - same table, same
// columns, two services each enforcing their own slice of the lifecycle.
@Entity
@Table(name = "facility_account", schema = "bizkredit_collateral_db")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"application", "business"})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class FacilityAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long facilityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "application_id", nullable = false)
    private LoanApplication application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private SMEBusiness business;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Product type is required")
    private ProductType productType;

    @NotNull(message = "Sanctioned limit is required")
    @Positive(message = "Sanctioned limit must be positive")
    private BigDecimal sanctionedLimit;

    private BigDecimal disbursedAmount;

    private BigDecimal outstandingBalance;

    @Positive(message = "Interest rate must be positive")
    private BigDecimal interestRate;

    private LocalDate expiryDate;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private FacilityStatus status = FacilityStatus.ACTIVE;
}
