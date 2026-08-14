package com.bizkredit.monitoring.enums;

// Links a FINANCIAL covenant to a specific, already-computed ratio on the
// applicant's financial statement (credit-service computes these
// automatically the moment a statement is entered - see
// FinancialAnalysisService). This is what makes automatic covenant
// checking possible at all: a covenant's `description` field alone
// ("Maintain Current Ratio >= 1.25") is free text - a human can read it,
// but nothing in the system could previously act on it. This enum is
// the structured link that lets CovenantDueScheduler compare an actual
// number against the threshold without a human doing that comparison
// by hand every time.
public enum FinancialMetric {
    CURRENT_RATIO,
    DEBT_EQUITY_RATIO,
    DSCR,
    NET_WORTH,
    EBITDA_MARGIN,
    // Non-financial covenants (submit documents, maintain insurance,
    // no additional borrowing) have no computable number behind them at
    // all - actually confirming those requires a human judgement call,
    // not a ratio comparison, so this is the honest "not automatable"
    // value rather than silently defaulting to a wrong metric.
    NONE
}
