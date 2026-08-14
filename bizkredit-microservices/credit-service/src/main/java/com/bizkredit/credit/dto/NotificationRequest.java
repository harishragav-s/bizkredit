package com.bizkredit.credit.dto;

/**
 * Body sent to monitoring-service's POST /api/notifications.
 *
 * category must be one of monitoring-service's own NotificationCategory
 * constants (APPLICATION, COLLATERAL, FACILITY, COVENANT, EWS, COMPLIANCE) -
 * it's sent as a plain string here rather than sharing an enum type across
 * services, then parsed by monitoring-service's own @Enumerated mapping.
 */
public record NotificationRequest(String message, String category) {
}
