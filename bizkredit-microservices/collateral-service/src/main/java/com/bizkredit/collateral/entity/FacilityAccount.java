package com.bizkredit.collateral.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bizkredit.collateral.enums.FacilityStatus;
import com.bizkredit.collateral.enums.ProductType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "facility_account")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class FacilityAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long facilityId;

    // Both owned by sme-loan-service - ids only, fetched over Feign
    // (SmeLoanGateway) wherever the full application/business is needed.
    // Not @NotNull: both are set by the service from request params after
    // @Valid has already run on the incoming request body, which never
    // includes them (the client sends applicationId/businessId as query
    // params on POST /api/facilities, not in the JSON body).
    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

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
