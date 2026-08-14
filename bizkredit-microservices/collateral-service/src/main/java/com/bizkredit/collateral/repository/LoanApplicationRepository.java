package com.bizkredit.collateral.repository;

import com.bizkredit.collateral.entity.LoanApplication;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Read access to the loan_application table (owned/written by sme-loan-service).
// collateral-service uses this to attach collateral records and facility
// accounts to an application.
@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    @EntityGraph(attributePaths = {"business"})
    Optional<LoanApplication> findById(Long id);

    // BP2-45/54 - renewal history for a facility (applications created as
    // renewals of it, most recent first).
    List<LoanApplication> findByRenewedFromFacilityIdOrderByApplicationDateDesc(Long facilityId);
}
