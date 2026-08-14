package com.bizkredit.collateral.enums;

// Status of a collateral record
public enum CollateralStatus {
    // Applicant declared this collateral themselves - description and an
    // estimated value only. Does NOT count toward coverage calculations
    // until a Collateral Evaluator confirms it (mirrors how an applicant's
    // repayment claim doesn't reduce the balance until RM verifies it).
    DISCLOSED,
    REGISTERED,     // Collateral registered, not yet charged
    CHARGED,        // Charged against a facility
    RELEASED,       // Released after loan closure
    DISPUTED        // Under legal dispute
}
