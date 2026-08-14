package com.bizkredit.credit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Read-only projection of sme-loan-service's SMEBusiness.
 *
 * This is a DTO, NOT a JPA entity - credit-service does not own the
 * sme_business table and must never persist it. Only the fields the
 * scorecard engine actually reads are declared, so sme-loan-service can
 * add columns without forcing a rebuild here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SMEBusinessDTO {
    private Long businessId;
    private String businessName;
    private String registrationNumber;
    private String entityType;
    private String industry;
    private Integer yearsInOperation;
    private BigDecimal annualTurnover;
    private Integer employeeCount;
    private String kycStatus;
    private Long applicantUserId;
    private String status;
}
