package com.bizkredit.collateral.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Read-only projection of credit-service's CreditProposal - just enough
 * to enumerate an application's proposals and look up decisions per one.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditProposalDTO {
    private Long proposalId;
    private Long applicationId;
    private String status;
    private BigDecimal suggestedAmount;
}
