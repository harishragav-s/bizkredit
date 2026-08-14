package com.bizkredit.collateral.repository;

import com.bizkredit.collateral.entity.CollateralRecord;
import com.bizkredit.collateral.enums.CollateralStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CollateralRecordRepository extends JpaRepository<CollateralRecord, Long> {

    List<CollateralRecord> findByApplicationId(Long applicationId);

    List<CollateralRecord> findByStatus(CollateralStatus status);

    // BP2-37 - revaluations due within N days (includes already-overdue ones).
    @Query("SELECT c FROM CollateralRecord c WHERE c.status != 'RELEASED' AND " +
           "c.nextRevaluationDate IS NOT NULL AND c.nextRevaluationDate <= :cutoff " +
           "ORDER BY c.nextRevaluationDate ASC")
    List<CollateralRecord> findDueForRevaluation(@Param("cutoff") LocalDate cutoff);
}
