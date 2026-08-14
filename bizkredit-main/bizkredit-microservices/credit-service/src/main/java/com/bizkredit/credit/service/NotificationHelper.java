package com.bizkredit.credit.service;

import com.bizkredit.credit.client.AuthServiceClient;
import com.bizkredit.credit.client.MonitoringServiceClient;
import com.bizkredit.credit.dto.NotificationRequest;
import com.bizkredit.credit.dto.UserDTO;
import com.bizkredit.credit.enums.NotificationCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

// Calls monitoring-service to create a real notification row in the
// database the frontend's notification bell actually queries.
// Previously this saved to a local repository backed by
// bizkredit_credit_db's own "notification" table - a different physical
// database from bizkredit_monitoring_db, so nothing raised here ever
// reached the user. Still fails silently - a notification failing to
// send must never break the actual business operation that triggered it
// (submitting an application, etc.).
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationHelper {

    private final MonitoringServiceClient monitoringServiceClient;
    private final AuthServiceClient authServiceClient;

    public void notify(Long userId, String message, NotificationCategory category) {
        if (userId == null) return;
        try {
            monitoringServiceClient.createNotification(userId,
                    new NotificationRequest(message, category.name()));
        } catch (Exception e) {
            log.warn("Failed to create notification for user {}: {}", userId, e.getMessage());
        }
    }

    // Broadcasts a notification to EVERY active user holding a given
    // role. Needed because roles like UNDERWRITING_MANAGER,
    // RELATIONSHIP_MANAGER and COLLATERAL_EVALUATOR have no
    // "assigned user" on an application - so when their step becomes
    // due, we notify all of them rather than one specific person.
    // Looks the role up via auth-service (Feign) rather than a local
    // cross-schema read of its users table.
    public void notifyRole(String role, String message, NotificationCategory category) {
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
