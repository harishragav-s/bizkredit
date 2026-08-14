package com.bizkredit.sme.repository;

import com.bizkredit.sme.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Write-only access to the shared notification table (see the
// Notification entity for why this is intentionally minimal). This
// service only ever saves new notifications; reads happen through
// monitoring-service.
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
