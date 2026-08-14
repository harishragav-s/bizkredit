package com.bizkredit.credit.repository;

import com.bizkredit.credit.entity.CreditProposal;
import com.bizkredit.credit.enums.ProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CreditProposalRepository extends JpaRepository<CreditProposal, Long> {

    Optional<CreditProposal> findByApplicationId(Long applicationId);

    List<CreditProposal> findAllByApplicationId(Long applicationId);

    List<CreditProposal> findByStatus(ProposalStatus status);

    List<CreditProposal> findByAnalystId(Long analystId);
}
