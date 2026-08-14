package com.bizkredit.collateral.repository;

import com.bizkredit.collateral.entity.Drawdown;
import com.bizkredit.collateral.enums.DrawdownStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DrawdownRepository extends JpaRepository<Drawdown, Long> {

    @EntityGraph(attributePaths = { "facility" })
    Optional<Drawdown> findById(Long id);

    @EntityGraph(attributePaths = { "facility" })
    List<Drawdown> findByFacility_FacilityId(Long facilityId);

    @EntityGraph(attributePaths = { "facility" })
    List<Drawdown> findByStatus(DrawdownStatus status);
}
