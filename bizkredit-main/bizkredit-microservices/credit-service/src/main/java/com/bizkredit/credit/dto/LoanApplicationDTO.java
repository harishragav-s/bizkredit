package com.bizkredit.credit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only projection of sme-loan-service's LoanApplication.
 *
 * `business` is nested because the scorecard needs business attributes
 * (industry, turnover, vintage) in the same call - fetching it separately
 * would double the number of HTTP round trips per scoring run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplicationDTO {
    private Long applicationId;
    private SMEBusinessDTO business;
    // sme-loan-service's LoanApplication.business field is @JsonIgnore'd
    // (it's a lazy JPA relation) and only exposes a computed `businessId`
    // getter instead - so `business` above can NEVER actually be populated
    // by Feign/Jackson deserialization; it's always null. This field is the
    // one that actually arrives over the wire, and is what must be used to
    // separately fetch the business via SmeGateway.getBusiness(businessId).
    private Long businessId;
    private String productType;
    private BigDecimal requestedAmount;
    private Integer tenure;
    private String purpose;
    private LocalDate applicationDate;
    private Long assignedAnalystId;
    private Long applicantUserId;
    private String status;
}
