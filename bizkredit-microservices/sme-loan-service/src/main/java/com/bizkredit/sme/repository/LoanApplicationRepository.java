package com.bizkredit.sme.repository;

import com.bizkredit.sme.entity.LoanApplication;
import com.bizkredit.sme.enums.ApplicationStatus;
import com.bizkredit.sme.enums.ProductType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {

    @EntityGraph(attributePaths = {"business"})
    Optional<LoanApplication> findById(Long id);

    @EntityGraph(attributePaths = {"business"})
    List<LoanApplication> findByBusiness_BusinessId(Long businessId);

    @EntityGraph(attributePaths = {"business"})
    List<LoanApplication> findByStatus(ApplicationStatus status);

    @EntityGraph(attributePaths = {"business"})
    List<LoanApplication> findByAssignedAnalystId(Long analystId);

    // Filtered query for GET /api/applications
    // EntityGraph is required here (same as findById/findByBusiness_BusinessId above) -
    // without it, `business` stays a lazy proxy. Since open-in-view=false, the Hibernate
    // session is closed by the time the controller serializes the response to JSON,
    // so Jackson hits a LazyInitializationException trying to read `business`, which
    // GlobalExceptionHandler's default branch turns into an opaque 500.
    @EntityGraph(attributePaths = {"business"})
    @Query("SELECT a FROM LoanApplication a WHERE " +
           "(:businessId IS NULL OR a.business.businessId = :businessId) AND " +
           "(:status IS NULL OR a.status = :status) AND " +
           "(:productType IS NULL OR a.productType = :productType) AND " +
           "(:applicantUserId IS NULL OR a.applicantUserId = :applicantUserId)")
    List<LoanApplication> findWithFilters(
            @Param("businessId") Long businessId,
            @Param("status") ApplicationStatus status,
            @Param("productType") ProductType productType,
            @Param("applicantUserId") Long applicantUserId
    );

    @EntityGraph(attributePaths = {"business"})
    List<LoanApplication> findByRenewedFromFacilityId(Long facilityId);
}
