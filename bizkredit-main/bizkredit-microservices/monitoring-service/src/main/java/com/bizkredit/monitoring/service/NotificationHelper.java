package com.bizkredit.monitoring.service;

import com.bizkredit.monitoring.client.AuthServiceClient;
import com.bizkredit.monitoring.entity.Notification;
import com.bizkredit.monitoring.enums.NotificationCategory;
import com.bizkredit.monitoring.enums.NotificationStatus;
import com.bizkredit.monitoring.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

// This is the real notification writer - monitoring-service owns the
// notification table, so (unlike the SLF4J-only stubs of the same name in
// the other services) this one actually persists a Notification row.
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationHelper {

    private final NotificationRepository notificationRepository;
    private final AuthServiceClient authServiceClient;

    // Creates a notification if the userId maps to a valid user (checked
    // over Feign against auth-service). Fails silently.
    public void notify(Long userId, String message, NotificationCategory category) {
        if (userId == null) return;
        try {
            var body = authServiceClient.getUser(userId);
            if (body == null || body.getData() == null) return;

            notificationRepository.save(Notification.builder()
                    .userId(userId)
                    .message(message)
                    .category(category)
                    .status(NotificationStatus.UNREAD)
                    .createdDate(LocalDate.now())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to create notification for user {}: {}", userId, e.getMessage());
        }
    }
}
