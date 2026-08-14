package com.bizkredit.credit.enums;

// BP2-17/18 - actions on credit proposals and underwriting decisions that
// require dual authorization. Mirrors collateral-service's MakerCheckerAction
// (which covers facility/collateral-side actions) rather than sharing an
// enum across services, since these two services must stay independently
// deployable.
public enum MakerCheckerAction {
    SUBMIT_PROPOSAL,
    APPROVE_DECISION
}
