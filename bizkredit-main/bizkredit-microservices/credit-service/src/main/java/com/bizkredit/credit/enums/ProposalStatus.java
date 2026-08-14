package com.bizkredit.credit.enums;

// Status flow for a credit proposal
// Draft -> Submitted -> ApprovedByManager/Declined -> Sanctioned
public enum ProposalStatus {
    DRAFT,
    SUBMITTED,
    APPROVED_BY_MANAGER,
    DECLINED,
    SANCTIONED
}
