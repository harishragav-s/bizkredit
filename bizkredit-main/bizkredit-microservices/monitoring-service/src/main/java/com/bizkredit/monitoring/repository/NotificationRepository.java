package com.bizkredit.monitoring.repository;

import com.bizkredit.monitoring.entity.Notification;
import com.bizkredit.monitoring.enums.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserId(Long userId);

    List<Notification> findByStatus(NotificationStatus status);

    long countByUserIdAndStatus(Long userId, NotificationStatus status);
}
