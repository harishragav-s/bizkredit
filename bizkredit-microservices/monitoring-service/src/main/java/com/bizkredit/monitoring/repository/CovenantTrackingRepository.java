package com.bizkredit.monitoring.repository;

import com.bizkredit.monitoring.entity.CovenantTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CovenantTrackingRepository extends JpaRepository<CovenantTracking, Long> {

    List<CovenantTracking> findByCovenantIdOrderByReviewDateDesc(Long covenantId);
}
