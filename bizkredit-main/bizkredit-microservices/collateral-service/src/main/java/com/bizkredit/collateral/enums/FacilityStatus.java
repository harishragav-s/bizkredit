package com.bizkredit.collateral.enums;

// Status of a facility account
public enum FacilityStatus {
    ACTIVE,     // Facility is live and operational
    EXPIRED,    // Facility tenure has ended
    CLOSED,     // Facility closed after full repayment
    NPA         // Non-Performing Asset - overdue beyond threshold
}
