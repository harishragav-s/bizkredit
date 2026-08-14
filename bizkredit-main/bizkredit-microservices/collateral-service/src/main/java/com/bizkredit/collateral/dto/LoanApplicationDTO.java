package com.bizkredit.collateral.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only projection of sme-loan-service's LoanApplication.
 * Only the fields collateral-service actually reads - the applicant to
 * notify, and the status to check before transitioning it further.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplicationDTO {
    private Long applicationId;
    private Long applicantUserId;
    private String status;
}
