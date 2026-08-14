package com.bizkredit.monitoring.repository;

import com.bizkredit.monitoring.entity.FacilityAccount;
import com.bizkredit.monitoring.enums.FacilityStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

// Read/write access to the facility_account table (owned by collateral-service).
// monitoring-service reads it to attach covenants/EWS/NPA records and to
// compute portfolio analytics, and writes to it in NPAClassificationService
// (flipping status to NPA on classification / back to ACTIVE on upgrade).
@Repository
public interface FacilityAccountRepository extends JpaRepository<FacilityAccount, Long> {

    @EntityGraph(attributePaths = {"application", "application.business", "business"})
    Optional<FacilityAccount> findById(Long id);

    @EntityGraph(attributePaths = {"application", "application.business", "business"})
    List<FacilityAccount> findByStatus(FacilityStatus status);

    // Portfolio analytics queries.
    // Declared as List<Object[]> (not a bare Object[]) even though
    // this query has no GROUP BY and always returns exactly one row -
    // Spring Data JPA's query execution is built around returning a
    // list of result rows for any query with multiple selected
    // columns, and a bare Object[] return type here is a known-fragile
    // pattern that silently produced an empty/malformed result instead
    // of throwing an error, which PortfolioService's null-check
    // fallback then quietly converted into "everything is zero" with
    // no visible sign anything had gone wrong.
    @Query("SELECT SUM(f.sanctionedLimit), SUM(f.outstandingBalance), COUNT(f) " +
           "FROM FacilityAccount f WHERE f.status = 'ACTIVE'")
    List<Object[]> getPortfolioSummary();

    @Query("SELECT f.status, COUNT(f), SUM(f.outstandingBalance) " +
           "FROM FacilityAccount f GROUP BY f.status")
    List<Object[]> getAssetQualityDistribution();

    @Query("SELECT b.industry, SUM(f.outstandingBalance) " +
           "FROM FacilityAccount f JOIN f.business b " +
           "WHERE f.status = 'ACTIVE' " +
           "GROUP BY b.industry ORDER BY SUM(f.outstandingBalance) DESC")
    List<Object[]> getSectorExposure();
}
