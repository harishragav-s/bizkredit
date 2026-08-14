package com.bizkredit.collateral.enums;

public enum RepaymentStatus {
    // Submitted by the SME Applicant themselves - claimed, but not yet
    // confirmed by anyone at the bank. Does NOT reduce the facility's
    // outstanding balance yet, since an unconfirmed claim of payment
    // isn't proof the money actually arrived.
    PENDING_VERIFICATION,
    // Recorded directly by RELATIONSHIP_MANAGER/ADMIN - the bank staff
    // recording it IS the confirmation (e.g. they're looking at a bank
    // statement showing the credit), so this applies to the balance
    // immediately, same as before.
    RECEIVED,
    // A PENDING_VERIFICATION repayment that RM has since confirmed -
    // applies to the balance at THIS point, not when it was first
    // submitted.
    VERIFIED,
    REVERSED
}
