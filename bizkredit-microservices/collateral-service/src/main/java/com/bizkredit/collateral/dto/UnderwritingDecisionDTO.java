package com.bizkredit.collateral.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Read-only projection of credit-service's UnderwritingDecision.
 *
 * Field names/types mirror the REAL entity credit-service serializes
 * (decisionDate is a LocalDate there, not LocalDateTime; the raw amount/
 * rate/tenure fields are approvedRate/tenure/specialConditions, not
 * "sanctioned*" - matching those exactly matters, a mismatched DTO
 * silently deserializes to nulls instead of failing loudly).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnderwritingDecisionDTO {
    private Long decisionId;
    private Long managerId;
    private BigDecimal sanctionedAmount;
    private BigDecimal approvedRate;
    private Integer tenure;
    private String specialConditions;
    private LocalDate decisionDate;
    private String status;
}
