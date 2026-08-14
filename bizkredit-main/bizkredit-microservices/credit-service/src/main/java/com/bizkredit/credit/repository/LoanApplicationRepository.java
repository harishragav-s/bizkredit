package com.bizkredit.credit.repository;

import com.bizkredit.credit.entity.LoanApplication;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// Read access to the loan_application table (owned/written by sme-loan-service).
// credit-service uses this to attach financial statements/proposals to an
// application and to resolve the application's business for scorecard lookups.
@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    @EntityGraph(attributePaths = {"business"})
    Optional<LoanApplication> findById(Long id);
}
