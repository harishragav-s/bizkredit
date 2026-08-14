package com.bizkredit.credit.repository;

import com.bizkredit.credit.entity.Promoter;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// Read access to the promoter table (owned/written by sme-loan-service).
// credit-service uses this to resolve promoter data for scorecard field lookups.
@Repository
public interface PromoterRepository extends JpaRepository<Promoter, Long> {

    @EntityGraph(attributePaths = {"business"})
    List<Promoter> findByBusiness_BusinessId(Long businessId);
}
