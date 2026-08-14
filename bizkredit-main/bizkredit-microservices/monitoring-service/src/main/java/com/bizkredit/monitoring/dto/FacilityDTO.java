package com.bizkredit.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only projection of collateral-service's FacilityAccount.
 *
 * `businessIndustry` is flattened onto this DTO deliberately. Portfolio
 * analytics needs to group exposure by industry, and the monolith did that
 * with a cross-table JPA join. Over HTTP there is no join, so the owning
 * service supplies the industry inline rather than making monitoring issue
 * an N+1 storm of per-business lookups.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FacilityDTO {
    private Long facilityId;
    private Long applicationId;
    private Long businessId;
    private String businessName;
    private String businessIndustry;
    private String facilityType;
    private BigDecimal sanctionedLimit;
    private BigDecimal outstandingBalance;
    private BigDecimal interestRate;
    private Integer tenure;
    private String status;
    private LocalDate sanctionDate;

    // collateral-service's FacilityAccount entity (what /api/facilities
    // actually serializes) calls this field `expiryDate`, not
    // `maturityDate` - without the alias this silently deserialized to
    // null on every Feign call, which the renewal-pipeline aggregation
    // depends on (see PortfolioService.withinDays). businessName,
    // businessIndustry, facilityType, and sanctionDate have the same
    // mismatch but nothing reads them today, so they're left alone rather
    // than risk changing behavior no one currently depends on.
    @JsonAlias("expiryDate")
    private LocalDate maturityDate;
}
