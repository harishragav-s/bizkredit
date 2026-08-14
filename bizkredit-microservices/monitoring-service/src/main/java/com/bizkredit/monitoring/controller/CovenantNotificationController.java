package com.bizkredit.monitoring.controller;

import com.bizkredit.monitoring.dto.ApiResponse;
import com.bizkredit.monitoring.entity.Covenant;
import com.bizkredit.monitoring.entity.EarlyWarningSignal;
import com.bizkredit.monitoring.entity.Notification;
import com.bizkredit.monitoring.enums.NotificationCategory;
import com.bizkredit.monitoring.enums.NotificationStatus;
import com.bizkredit.monitoring.service.CovenantNotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Risk Monitoring & Portfolio")
@RestController
@RequiredArgsConstructor
public class CovenantNotificationController {

    private final CovenantNotificationService covenantService;

    // Covenant

    @PostMapping("/api/facilities/{facilityId}/covenants")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Covenant>> createCovenant(
            @PathVariable Long facilityId,
            @Valid @RequestBody Covenant covenant) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Covenant created",
                        covenantService.createCovenant(facilityId, covenant)));
    }

    @GetMapping("/api/facilities/{facilityId}/covenants")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','CREDIT_ANALYST','ADMIN')")
    public ResponseEntity<ApiResponse<List<Covenant>>> getCovenants(
            @PathVariable Long facilityId) {

        return ResponseEntity.ok(ApiResponse.ok("Covenants fetched",
                covenantService.getCovenantsByFacility(facilityId)));
    }

    @PatchMapping("/api/facilities/{facilityId}/covenants/{id}/waive")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Covenant>> waiveCovenant(
            @PathVariable Long facilityId,
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.ok("Covenant waived",
                covenantService.waiveCovenant(id)));
    }

    // BP2-23 - Covenant Tracking (the missing periodic actuals-vs-threshold record)

    @PostMapping("/api/covenants/{covenantId}/tracking")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<com.bizkredit.monitoring.entity.CovenantTracking>> createTracking(
            @PathVariable Long covenantId,
            @Valid @RequestBody com.bizkredit.monitoring.entity.CovenantTracking tracking) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Compliance recorded", covenantService.createTracking(covenantId, tracking)));
    }

    @GetMapping("/api/covenants/{covenantId}/tracking")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','CREDIT_ANALYST','ADMIN')")
    public ResponseEntity<ApiResponse<List<com.bizkredit.monitoring.entity.CovenantTracking>>> getTrackingHistory(
            @PathVariable Long covenantId) {

        return ResponseEntity.ok(ApiResponse.ok("Compliance history fetched",
                covenantService.getTrackingHistory(covenantId)));
    }

    // BP2-39 - Watchlist classification
    @GetMapping("/api/covenants/watchlist")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','CREDIT_ANALYST','ADMIN')")
    public ResponseEntity<ApiResponse<List<java.util.Map<String, Object>>>> getWatchlist() {
        return ResponseEntity.ok(ApiResponse.ok("Watchlist fetched", covenantService.getWatchlist()));
    }

    // BP2-23/39 automation - runs the same overdue-tracking check the
    // nightly scheduler runs, on demand. Useful for verifying the effect
    // immediately rather than waiting for the actual scheduled time.
    @PostMapping("/api/covenants/check-due")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<String>> checkOverdueTracking() {
        int overdue = covenantService.checkOverdueTracking();
        return ResponseEntity.ok(ApiResponse.ok(
                overdue + " covenant(s) overdue for compliance review - reminders sent", null));
    }

    // EWS

    @GetMapping("/api/facilities/{facilityId}/ews")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','CREDIT_ANALYST','ADMIN')")
    public ResponseEntity<ApiResponse<List<EarlyWarningSignal>>> getEWS(
            @PathVariable Long facilityId) {

        return ResponseEntity.ok(ApiResponse.ok("EWS fetched",
                covenantService.getEWSByFacility(facilityId)));
    }

    // Notifications

    @PostMapping("/api/notifications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Notification>> createNotification(
            @RequestParam Long userId,
            @Valid @RequestBody Notification notification) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Notification created",
                        covenantService.createNotification(userId, notification)));
    }

    @GetMapping("/api/notifications")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','CREDIT_ANALYST','RELATIONSHIP_MANAGER','UNDERWRITING_MANAGER','COLLATERAL_EVALUATOR','ADMIN')")
    public ResponseEntity<ApiResponse<List<Notification>>> getNotifications(
            @RequestParam Long userId,
            @RequestParam(required = false) NotificationCategory category,
            @RequestParam(required = false) NotificationStatus status) {

        return ResponseEntity.ok(ApiResponse.ok("Notifications fetched",
                covenantService.getNotificationsFiltered(userId, category, status)));
    }

    @PatchMapping("/api/notifications/{id}/read")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','CREDIT_ANALYST','RELATIONSHIP_MANAGER','UNDERWRITING_MANAGER','COLLATERAL_EVALUATOR','ADMIN')")
    public ResponseEntity<ApiResponse<Notification>> markRead(@PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.ok("Notification marked read",
                covenantService.markAsRead(id)));
    }

    @PatchMapping("/api/notifications/{id}/dismiss")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','CREDIT_ANALYST','RELATIONSHIP_MANAGER','UNDERWRITING_MANAGER','COLLATERAL_EVALUATOR','ADMIN')")
    public ResponseEntity<ApiResponse<Notification>> dismiss(@PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.ok("Notification dismissed",
                covenantService.markAsDismissed(id)));
    }

}
