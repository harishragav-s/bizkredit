package com.bizkredit.credit.repository;

import com.bizkredit.credit.entity.ScorecardModel;
import com.bizkredit.credit.enums.ProductType;
import com.bizkredit.credit.enums.ScorecardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScorecardModelRepository extends JpaRepository<ScorecardModel, Long> {

    // No @EntityGraph needed on parameters/ratingBands here - both
    // collections are annotated with @Fetch(FetchMode.SUBSELECT) on
    // ScorecardModel itself, which makes them load eagerly via their
    // own follow-up queries automatically. Adding @EntityGraph for
    // them here would tell Hibernate to also JOIN FETCH them, which
    // reintroduces MultipleBagFetchException (see ScorecardModel for
    // the full explanation) - the two mechanisms conflict, not stack.
    Optional<ScorecardModel> findById(Long id);

    List<ScorecardModel> findByStatus(ScorecardStatus status);

    // Returns list - use .isEmpty() check in service for uniqueness enforcement
    List<ScorecardModel> findByProductTypeAndStatus(ProductType productType, ScorecardStatus status);
}
