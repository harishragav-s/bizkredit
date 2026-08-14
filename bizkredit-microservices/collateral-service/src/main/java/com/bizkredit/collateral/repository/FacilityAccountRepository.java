package com.bizkredit.collateral.repository;

import com.bizkredit.collateral.entity.FacilityAccount;
import com.bizkredit.collateral.enums.FacilityStatus;
import com.bizkredit.collateral.enums.ProductType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FacilityAccountRepository extends JpaRepository<FacilityAccount, Long> {

    List<FacilityAccount> findByBusinessId(Long businessId);

    List<FacilityAccount> findByApplicationId(Long applicationId);

    List<FacilityAccount> findByStatus(FacilityStatus status);

    // Filtered query for GET /api/facilities
    @Query("SELECT f FROM FacilityAccount f WHERE " +
           "(:businessId IS NULL OR f.businessId = :businessId) AND " +
           "(:status IS NULL OR f.status = :status) AND " +
           "(:productType IS NULL OR f.productType = :productType)")
    List<FacilityAccount> findWithFilters(
            @Param("businessId") Long businessId,
            @Param("status") FacilityStatus status,
            @Param("productType") ProductType productType
    );

    // Facilities expiring within N days (for renewal pipeline)
    @Query("SELECT f FROM FacilityAccount f WHERE " +
           "f.status = 'ACTIVE' AND " +
           "f.expiryDate BETWEEN :now AND :cutoff " +
           "ORDER BY f.expiryDate ASC")
    List<FacilityAccount> findExpiringFacilities(
            @Param("now") LocalDate now,
            @Param("cutoff") LocalDate cutoff
    );

    // Portfolio analytics queries - declared as List<Object[]>, not a
    // bare Object[], even though this specific query has no GROUP BY
    // and always returns exactly one row. A bare Object[] return type
    // for a multi-column aggregate is a known-fragile Spring Data JPA
    // pattern (see monitoring-service's PortfolioService for the full
    // explanation of the bug this caused there). Not currently called
    // from anywhere in this service - portfolio analytics lives in
    // monitoring-service - but kept consistent so this doesn't become
    // a landmine if it's ever wired up here too.
    @Query("SELECT SUM(f.sanctionedLimit), SUM(f.outstandingBalance), COUNT(f) " +
           "FROM FacilityAccount f WHERE f.status = 'ACTIVE'")
    List<Object[]> getPortfolioSummary();

    @Query("SELECT f.status, COUNT(f), SUM(f.outstandingBalance) " +
           "FROM FacilityAccount f GROUP BY f.status")
    List<Object[]> getAssetQualityDistribution();

    // NOTE: sector-exposure-by-industry (formerly a JOIN to the local
    // SMEBusiness shadow entity's `industry` column) was removed here -
    // industry now lives only in sme-loan-service. It was unused dead
    // code in this service (portfolio analytics lives in
    // monitoring-service); if this is ever needed here, aggregate in
    // application code using SmeLoanGateway.getBusiness() per distinct
    // businessId rather than reintroducing a cross-schema join.
}
