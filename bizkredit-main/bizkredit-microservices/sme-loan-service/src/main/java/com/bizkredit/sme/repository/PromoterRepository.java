package com.bizkredit.sme.repository;

import com.bizkredit.sme.entity.Promoter;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromoterRepository extends JpaRepository<Promoter, Long> {

    // EntityGraph needed - `business` is LAZY and open-in-view=false (same bug pattern
    // as LoanApplicationRepository).
    @EntityGraph(attributePaths = {"business"})
    Optional<Promoter> findById(Long id);

    // Get all promoters for a specific business
    @EntityGraph(attributePaths = {"business"})
    List<Promoter> findByBusiness_BusinessId(Long businessId);
}
