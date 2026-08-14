package com.bizkredit.collateral.repository;

import com.bizkredit.collateral.entity.CollateralRevaluation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CollateralRevaluationRepository extends JpaRepository<CollateralRevaluation, Long> {

    @EntityGraph(attributePaths = { "collateral" })
    Optional<CollateralRevaluation> findById(Long id);

    @EntityGraph(attributePaths = { "collateral" })
    List<CollateralRevaluation> findByCollateral_CollateralId(Long collateralId);
}
