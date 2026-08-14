package com.bizkredit.sme.repository;

import com.bizkredit.sme.entity.LoanProduct;
import com.bizkredit.sme.enums.LoanProductStatus;
import com.bizkredit.sme.enums.ProductType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {

    boolean existsByProductCode(String productCode);

    // EntityGraph needed - requiredDocuments is an @ElementCollection
    // (LAZY by default) and open-in-view=false, so without it Jackson
    // hits a LazyInitializationException trying to serialize it once
    // the Hibernate session is closed (same bug pattern as
    // LoanApplicationRepository elsewhere in this service).
    @EntityGraph(attributePaths = {"requiredDocuments"})
    Optional<LoanProduct> findById(Long id);

    @EntityGraph(attributePaths = {"requiredDocuments"})
    Optional<LoanProduct> findByProductCode(String productCode);

    @EntityGraph(attributePaths = {"requiredDocuments"})
    List<LoanProduct> findByStatus(LoanProductStatus status);

    @EntityGraph(attributePaths = {"requiredDocuments"})
    List<LoanProduct> findByProductTypeAndStatus(ProductType productType, LoanProductStatus status);
}
