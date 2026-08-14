package com.bizkredit.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only projection of sme-loan-service's SMEBusiness - just the
 * industry, which is all PortfolioService.getSectorExposure() needs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SMEBusinessDTO {
    private Long businessId;
    private String industry;
}
