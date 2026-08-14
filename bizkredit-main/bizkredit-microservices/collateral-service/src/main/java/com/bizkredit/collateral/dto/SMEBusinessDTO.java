package com.bizkredit.collateral.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only projection of sme-loan-service's SMEBusiness.
 * collateral-service only uses this to validate a business exists before
 * attaching a facility to it - it stores just the businessId afterward.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SMEBusinessDTO {
    private Long businessId;
    private String businessName;
    private String industry;
}
