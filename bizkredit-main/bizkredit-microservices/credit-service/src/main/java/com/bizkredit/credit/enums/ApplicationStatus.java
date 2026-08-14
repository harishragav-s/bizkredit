package com.bizkredit.credit.enums;

// Status flow for a loan application
// Draft -> Submitted -> InReview -> UnderwritingApproval -> Sanctioned/Rejected -> Disbursed
public enum ApplicationStatus {
    DRAFT,
    SUBMITTED,
    IN_REVIEW,
    UNDERWRITING_APPROVAL,
    SANCTIONED,
    REJECTED,
    DISBURSED
}
