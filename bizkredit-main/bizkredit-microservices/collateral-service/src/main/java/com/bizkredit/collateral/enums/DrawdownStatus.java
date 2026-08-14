package com.bizkredit.collateral.enums;

// Status of a drawdown request
public enum DrawdownStatus {
    REQUESTED,  // Submitted by business
    APPROVED,   // Approved by bank
    DISBURSED,  // Amount transferred to business
    REPAID,     // Fully repaid
    OVERDUE     // Repayment date passed without payment
}
