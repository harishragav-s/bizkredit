package com.bizkredit.credit.repository;

import com.bizkredit.credit.entity.UnderwritingDecision;
import com.bizkredit.credit.enums.DecisionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnderwritingDecisionRepository extends JpaRepository<UnderwritingDecision, Long> {

    // proposal is still a local, credit-service-owned relation (CreditProposal
    // lives in this service's own schema) - only the application it points to
    // (via CreditProposal.applicationId) is external, and that's a plain id,
    // not a JPA relation, so no entity graph is needed for it.
    @EntityGraph(attributePaths = {"proposal"})
    Optional<UnderwritingDecision> findById(Long id);

    @EntityGraph(attributePaths = {"proposal"})
    Optional<UnderwritingDecision> findByProposal_ProposalId(Long proposalId);

    @EntityGraph(attributePaths = {"proposal"})
    List<UnderwritingDecision> findByStatus(DecisionStatus status);

    @EntityGraph(attributePaths = {"proposal"})
    List<UnderwritingDecision> findByManagerId(Long managerId);
}
