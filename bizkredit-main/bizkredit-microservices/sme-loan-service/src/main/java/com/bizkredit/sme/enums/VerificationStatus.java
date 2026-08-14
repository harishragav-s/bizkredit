package com.bizkredit.sme.enums;

// Verification status of an uploaded document
public enum VerificationStatus {
    PENDING,    // Just uploaded, not yet reviewed
    VERIFIED,   // Reviewed and accepted
    DEFICIENT,  // Needs resubmission
    REJECTED    // Not acceptable
}
