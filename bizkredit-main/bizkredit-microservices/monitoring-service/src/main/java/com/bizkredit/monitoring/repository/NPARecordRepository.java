package com.bizkredit.monitoring.repository;

import com.bizkredit.monitoring.entity.NPARecord;
import com.bizkredit.monitoring.enums.NPAProvisioningCategory;
import com.bizkredit.monitoring.enums.NPARecordStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NPARecordRepository extends JpaRepository<NPARecord, Long> {

    List<NPARecord> findByFacilityId(Long facilityId);

    List<NPARecord> findByProvisioningCategoryAndStatus(NPAProvisioningCategory category, NPARecordStatus status);

    List<NPARecord> findByStatus(NPARecordStatus status);

    Optional<NPARecord> findByFacilityIdAndStatus(Long facilityId, NPARecordStatus status);
}
