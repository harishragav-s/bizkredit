package com.bizkredit.monitoring.repository;

import com.bizkredit.monitoring.entity.Covenant;
import com.bizkredit.monitoring.enums.CovenantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CovenantRepository extends JpaRepository<Covenant, Long> {

    List<Covenant> findByFacilityId(Long facilityId);

    List<Covenant> findByStatus(CovenantStatus status);
}
