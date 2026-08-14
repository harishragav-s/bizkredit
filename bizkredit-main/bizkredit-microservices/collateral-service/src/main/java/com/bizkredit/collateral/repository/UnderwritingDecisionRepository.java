package com.bizkredit.collateral.repository;

import com.bizkredit.collateral.entity.UnderwritingDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// The "sanctioned limit cannot exceed what underwriting approved" check
// this repository used to back (via a cross-service JPQL join through
// CreditProposal -> LoanApplication, which no longer exists as a
// navigable relation) now lives in
// CollateralFacilityService.createFacility(), using
// CreditGateway.getLatestDecisionForApplication() (a Feign call) instead.
// This repository has no query methods of its own anymore - kept only
// so UnderwritingDecision rows collateral-service itself owns (if any)
// remain a registered JPA repository.
@Repository
public interface UnderwritingDecisionRepository extends JpaRepository<UnderwritingDecision, Long> {
}
