package com.bizkredit.monitoring.repository;

import com.bizkredit.monitoring.entity.Drawdown;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

// Read access to the drawdown table (owned/written by collateral-service)
// for most purposes, plus one narrow write: NPAClassificationService
// flips a drawdown's status to OVERDUE when its due date has passed
// and it's still unpaid - the same shared-database pattern already
// used for FacilityAccount's NPA status transition in this service.
@Repository
public interface DrawdownRepository extends JpaRepository<Drawdown, Long> {

    @EntityGraph(attributePaths = {"facility"})
    List<Drawdown> findByFacility_FacilityId(Long facilityId);
}
