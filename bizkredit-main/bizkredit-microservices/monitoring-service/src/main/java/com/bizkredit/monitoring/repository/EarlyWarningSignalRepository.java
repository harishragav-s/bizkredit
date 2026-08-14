package com.bizkredit.monitoring.repository;

import com.bizkredit.monitoring.entity.EarlyWarningSignal;
import com.bizkredit.monitoring.enums.EWSSeverity;
import com.bizkredit.monitoring.enums.EWSStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EarlyWarningSignalRepository extends JpaRepository<EarlyWarningSignal, Long> {

    List<EarlyWarningSignal> findByFacilityId(Long facilityId);

    List<EarlyWarningSignal> findBySeverity(EWSSeverity severity);

    List<EarlyWarningSignal> findByStatus(EWSStatus status);
}
