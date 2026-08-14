package com.bizkredit.collateral.service;

import com.bizkredit.collateral.client.AuthServiceClient;
import com.bizkredit.collateral.client.MonitoringServiceClient;
import com.bizkredit.collateral.dto.NotificationRequest;
import com.bizkredit.collateral.dto.UserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

// Calls monitoring-service to create a real notification row in the
// database the frontend's notification bell actually queries. Previously
// this saved to a local repository backed by bizkredit_collateral_db's own
// "notification" table - a different physical database from
// bizkredit_monitoring_db, so nothing raised here ever reached the user.
// Fails silently so a notification problem never breaks the business
// operation.
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationHelper {

    private final MonitoringServiceClient monitoringServiceClient;
    private final AuthServiceClient authServiceClient;

    public void notify(Long userId, String message, String category) {
        if (userId == null) return;
        try {
            monitoringServiceClient.createNotification(userId, new NotificationRequest(message, category));
        } catch (Exception e) {
            log.warn("Failed to create notification for user {}: {}", userId, e.getMessage());
        }
    }

    // Broadcasts to all active users with the given role. Looks the role up
    // via auth-service (Feign) rather than a local cross-schema read of its
    // users table.
    public void notifyRole(String role, String message, String category) {
        try {
            var body = authServiceClient.getUsersByRole(role);
            List<UserDTO> users = (body == null || body.getData() == null) ? List.of() : body.getData();
            for (UserDTO u : users) {
                if ("Active".equalsIgnoreCase(u.getStatus())) {
                    notify(u.getUserId(), message, category);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to broadcast notification to role {}: {}", role, e.getMessage());
        }
    }
}
