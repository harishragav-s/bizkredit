package com.bizkredit.sme.dto;

/**
 * Body sent to monitoring-service's POST /api/notifications.
 *
 * category must be one of monitoring-service's own NotificationCategory
 * constants (APPLICATION, COLLATERAL, FACILITY, COVENANT, EWS, COMPLIANCE).
 */
public record NotificationRequest(String message, String category) {
}
