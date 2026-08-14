package com.bizkredit.monitoring.service;

import com.bizkredit.monitoring.client.AuthServiceClient;
import com.bizkredit.monitoring.client.CollateralGateway;
import com.bizkredit.monitoring.client.CreditServiceClient;
import com.bizkredit.monitoring.entity.Covenant;
import com.bizkredit.monitoring.entity.EarlyWarningSignal;
import com.bizkredit.monitoring.entity.Notification;
import com.bizkredit.monitoring.repository.CovenantRepository;
import com.bizkredit.monitoring.repository.EarlyWarningSignalRepository;
import com.bizkredit.monitoring.repository.NotificationRepository;
import com.bizkredit.monitoring.enums.CovenantStatus;
import com.bizkredit.monitoring.enums.EWSStatus;
import com.bizkredit.monitoring.enums.NotificationCategory;
import com.bizkredit.monitoring.enums.NotificationStatus;
import com.bizkredit.monitoring.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CovenantNotificationService {

    private final CovenantRepository covenantRepository;
    private final EarlyWarningSignalRepository ewsRepository;
    private final NotificationRepository notificationRepository;
    private final com.bizkredit.monitoring.repository.CovenantTrackingRepository trackingRepository;
    private final CollateralGateway collateralGateway;
    private final CreditServiceClient creditServiceClient;
    private final AuthServiceClient authServiceClient;

    @Transactional
    public Covenant createCovenant(Long facilityId, Covenant covenant) {
        collateralGateway.getFacility(facilityId); // validates the facility exists

        covenant.setFacilityId(facilityId);
        covenant.setStatus(CovenantStatus.ACTIVE);

        return covenantRepository.save(covenant);
    }

    /**
     * Broadcasts a notification to every Active user holding the given
     * role. Previously missing entirely in this service - covenant breach
     * (createTracking) and EWS creation had no way to notify anyone beyond
     * a single known userId, so those events silently reached no one.
     * Best-effort: notification delivery must never break the underlying
     * covenant/EWS write it's attached to.
     */
    private void notifyRole(String role, String message, NotificationCategory category) {
        try {
            var body = authServiceClient.getUsersByRole(role);
            List<com.bizkredit.monitoring.dto.UserDTO> users = (body == null || body.getData() == null)
                    ? List.of() : body.getData();
            for (var u : users) {
                if ("Active".equalsIgnoreCase(u.getStatus())) {
                    notificationRepository.save(Notification.builder()
                            .userId(u.getUserId())
                            .message(message)
                            .category(category)
                            .status(NotificationStatus.UNREAD)
                            .createdDate(LocalDate.now())
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to broadcast notification to role {}: {}", role, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<Covenant> getCovenantsByFacility(Long facilityId) {
        return covenantRepository.findByFacilityId(facilityId);
    }

    @Transactional
    public Covenant updateCovenant(Long covenantId, Covenant updates) {
        Covenant covenant = covenantRepository.findById(covenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Covenant not found"));

        if (updates.getDescription() != null) {
            covenant.setDescription(updates.getDescription());
        }

        if (updates.getThresholdValue() != null) {
            covenant.setThresholdValue(updates.getThresholdValue());
        }

        if (updates.getMonitoringFrequency() != null) {
            covenant.setMonitoringFrequency(updates.getMonitoringFrequency());
        }

        return covenantRepository.save(covenant);
    }

    // BP2-23 AC - waive a covenant (Admin/RM only, enforced at controller level).
    @Transactional
    public Covenant waiveCovenant(Long covenantId) {
        Covenant covenant = covenantRepository.findById(covenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Covenant not found"));
        covenant.setStatus(CovenantStatus.WAIVED);
        return covenantRepository.save(covenant);
    }

    /**
     * BP2-23 - records compliance for a period, auto-determining
     * ComplianceStatus by comparing ActualValue against the covenant's
     * ThresholdValue (copied at time of review, per spec, so later
     * threshold edits don't retroactively change a past judgement).
     *
     * On breach: updates the Covenant's own Status to BREACHED, raises an
     * EWS signal (CovenantBreach), and - per BP2-39 - classifies the
     * facility into a watchlist category based on how many *consecutive*
     * periods have now been breached, escalating severity the same way
     * NPAClassificationService escalates overdue-day severity.
     */
    @Transactional
    public com.bizkredit.monitoring.entity.CovenantTracking createTracking(
            Long covenantId, com.bizkredit.monitoring.entity.CovenantTracking tracking) {
        Covenant covenant = covenantRepository.findById(covenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Covenant not found"));

        tracking.setCovenantId(covenantId);
        tracking.setThresholdValue(covenant.getThresholdValue());
        tracking.setReviewDate(tracking.getReviewDate() != null ? tracking.getReviewDate() : LocalDate.now());

        com.bizkredit.monitoring.enums.ComplianceStatus status;
        if (tracking.getActualValue() == null) {
            status = com.bizkredit.monitoring.enums.ComplianceStatus.DATA_AWAITED;
        } else if (covenant.getCovenantType() == com.bizkredit.monitoring.enums.CovenantType.FINANCIAL
                && covenant.getThresholdValue() != null) {
            // Financial covenants here are USUALLY expressed as a minimum
            // ("Current Ratio >= 1.5", "DSCR >= 1.1") - breached when the
            // actual falls below the threshold. Debt-Equity Ratio is the
            // one metric in this domain that runs the other way ("D/E
            // Ratio <= 2.0" - a LOWER number is healthier) - treating it
            // with the same ">= is compliant" rule as every other metric
            // would silently mark an over-leveraged business as compliant.
            boolean isMaximumMetric = covenant.getFinancialMetric()
                    == com.bizkredit.monitoring.enums.FinancialMetric.DEBT_EQUITY_RATIO;
            boolean compliant = isMaximumMetric
                    ? tracking.getActualValue().compareTo(covenant.getThresholdValue()) <= 0
                    : tracking.getActualValue().compareTo(covenant.getThresholdValue()) >= 0;
            status = compliant
                    ? com.bizkredit.monitoring.enums.ComplianceStatus.COMPLIANT
                    : com.bizkredit.monitoring.enums.ComplianceStatus.BREACHED;
        } else {
            // Non-financial covenants have no numeric threshold to compare -
            // recording a non-null actual value is itself the compliance signal.
            status = com.bizkredit.monitoring.enums.ComplianceStatus.COMPLIANT;
        }
        tracking.setComplianceStatus(status);

        var saved = trackingRepository.save(tracking);

        if (status == com.bizkredit.monitoring.enums.ComplianceStatus.BREACHED) {
            covenant.setStatus(CovenantStatus.BREACHED);
            covenantRepository.save(covenant);

            int consecutiveBreaches = countConsecutiveBreaches(covenantId);
            var severity = consecutiveBreaches >= 3 ? com.bizkredit.monitoring.enums.EWSSeverity.RED
                    : consecutiveBreaches == 2 ? com.bizkredit.monitoring.enums.EWSSeverity.AMBER
                    : com.bizkredit.monitoring.enums.EWSSeverity.GREEN;

            ewsRepository.save(EarlyWarningSignal.builder()
                    .facilityId(covenant.getFacilityId())
                    .signalType(com.bizkredit.monitoring.enums.EWSSignalType.COVENANT_BREACH)
                    .severity(severity)
                    .detectedDate(LocalDate.now())
                    .status(EWSStatus.OPEN)
                    .build());

            notifyRole("RELATIONSHIP_MANAGER",
                    "Covenant breach on facility #" + covenant.getFacilityId() + " for period " + tracking.getPeriod()
                            + " (" + consecutiveBreaches + " consecutive breach" + (consecutiveBreaches > 1 ? "es" : "") + ")",
                    NotificationCategory.COVENANT);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<com.bizkredit.monitoring.entity.CovenantTracking> getTrackingHistory(Long covenantId) {
        return trackingRepository.findByCovenantIdOrderByReviewDateDesc(covenantId);
    }

    /**
     * BP2-23/39 automation - previously, covenant tracking was entirely
     * manual: an RM had to remember, on their own, to open each facility
     * and log a period's compliance. Nothing ever reminded them, and
     * nothing ever flagged a covenant that had gone silent. This mirrors
     * NPAClassificationScheduler's pattern: for every ACTIVE covenant,
     * compute when its NEXT review is due from its MonitoringFrequency
     * and either its last tracking entry's reviewDate, or - if none
     * exists yet - the covenant's own createdDate as the baseline. Any
     * covenant past that due date gets a reminder notification.
     *
     * This does NOT auto-generate a compliance result - a real actual
     * value (e.g. this quarter's Current Ratio) still has to come from a
     * human who has that financial data. What this automates is the
     * REMINDER that it's due, not the judgement call itself.
     */
    @Transactional(readOnly = true)
    public int checkOverdueTracking() {
        List<Covenant> activeCovenants = covenantRepository.findByStatus(CovenantStatus.ACTIVE);
        int overdueCount = 0;

        for (Covenant covenant : activeCovenants) {
            var history = trackingRepository.findByCovenantIdOrderByReviewDateDesc(covenant.getCovenantId());
            LocalDate baseline = history.isEmpty() ? covenant.getCreatedDate() : history.get(0).getReviewDate();
            if (baseline == null) continue;

            int intervalDays = switch (covenant.getMonitoringFrequency()) {
                case MONTHLY -> 30;
                case QUARTERLY -> 90;
                case ANNUAL -> 365;
            };
            LocalDate dueDate = baseline.plusDays(intervalDays);

            if (LocalDate.now().isAfter(dueDate)) {
                overdueCount++;

                BigDecimal autoValue = tryAutoEvaluate(covenant);
                if (autoValue != null) {
                    // Real automatic tracking: the actual ratio comes
                    // straight from the applicant's own financial
                    // statement, compliance is computed by createTracking
                    // (with the correct direction per metric), and the RM
                    // is told the OUTCOME, not just "please go check".
                    var tracking = com.bizkredit.monitoring.entity.CovenantTracking.builder()
                            .period(LocalDate.now().toString())
                            .actualValue(autoValue)
                            .build();
                    createTracking(covenant.getCovenantId(), tracking);

                    notifyRole("RELATIONSHIP_MANAGER",
                            "Covenant #" + covenant.getCovenantId() + " (\"" + covenant.getDescription()
                                    + "\") auto-evaluated from the latest financial statement: "
                                    + autoValue + " vs threshold " + covenant.getThresholdValue()
                                    + " on facility #" + covenant.getFacilityId(),
                            NotificationCategory.COVENANT);
                } else {
                    // Non-financial covenant, no financialMetric mapped, or
                    // no financial statement available yet to evaluate
                    // against - still just a reminder, same as before.
                    long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(dueDate, LocalDate.now());
                    notifyRole("RELATIONSHIP_MANAGER",
                            "Compliance review overdue by " + daysOverdue + " day(s) for covenant #"
                                    + covenant.getCovenantId() + " (\"" + covenant.getDescription()
                                    + "\") on facility #" + covenant.getFacilityId(),
                            NotificationCategory.COVENANT);
                }
            }
        }
        return overdueCount;
    }

    /**
     * Pulls the applicant's most recent financial statement (via Feign to
     * credit-service, through the facility's application) and extracts
     * whichever ratio this covenant is mapped to. Returns null - meaning
     * "fall back to a reminder" - for non-financial covenants, covenants
     * never mapped to a metric, or if no statement exists yet to read.
     */
    private BigDecimal tryAutoEvaluate(Covenant covenant) {
        if (covenant.getFinancialMetric() == null
                || covenant.getFinancialMetric() == com.bizkredit.monitoring.enums.FinancialMetric.NONE) {
            return null;
        }
        try {
            var facility = collateralGateway.getFacility(covenant.getFacilityId());
            if (facility == null || facility.getApplicationId() == null) return null;

            var body = creditServiceClient.getStatements(facility.getApplicationId());
            var statements = (body == null || body.getData() == null) ? List.<com.bizkredit.monitoring.dto.FinancialStatementDTO>of() : body.getData();
            if (statements.isEmpty()) return null;

            // Same "latest = last in the list" convention used everywhere
            // else in this codebase that reads financial statements.
            var latest = statements.get(statements.size() - 1);

            return switch (covenant.getFinancialMetric()) {
                case CURRENT_RATIO -> latest.getCurrentRatio();
                case DEBT_EQUITY_RATIO -> latest.getDebtEquityRatio();
                case DSCR -> latest.getDscr();
                case NET_WORTH -> latest.getNetWorth();
                case EBITDA_MARGIN -> (latest.getRevenue() != null && latest.getRevenue().compareTo(BigDecimal.ZERO) > 0 && latest.getEbitda() != null)
                        ? latest.getEbitda().multiply(BigDecimal.valueOf(100)).divide(latest.getRevenue(), 2, java.math.RoundingMode.HALF_UP)
                        : null;
                case NONE -> null;
            };
        } catch (Exception e) {
            log.warn("Could not auto-evaluate covenant {}: {}", covenant.getCovenantId(), e.getMessage());
            return null;
        }
    }

    // BP2-39 - watchlist classification: SMA-0/1/2 based on consecutive
    // breached periods for a covenant, mirroring the overdue-day escalation
    // pattern NPAClassificationService already uses for drawdowns.
    private int countConsecutiveBreaches(Long covenantId) {
        var history = trackingRepository.findByCovenantIdOrderByReviewDateDesc(covenantId);
        int count = 0;
        for (var t : history) {
            if (t.getComplianceStatus() == com.bizkredit.monitoring.enums.ComplianceStatus.BREACHED) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * BP2-39 - portfolio-wide watchlist: every facility with at least one
     * currently-breached covenant, classified by its worst covenant's
     * consecutive-breach count.
     */
    @Transactional(readOnly = true)
    public List<java.util.Map<String, Object>> getWatchlist() {
        List<Covenant> breached = covenantRepository.findByStatus(CovenantStatus.BREACHED);

        return breached.stream()
                .collect(java.util.stream.Collectors.groupingBy(Covenant::getFacilityId))
                .entrySet().stream()
                .map(entry -> {
                    int worst = entry.getValue().stream()
                            .mapToInt(c -> countConsecutiveBreaches(c.getCovenantId()))
                            .max().orElse(0);
                    var category = worst >= 3 ? com.bizkredit.monitoring.enums.WatchlistCategory.SMA_2
                            : worst == 2 ? com.bizkredit.monitoring.enums.WatchlistCategory.SMA_1
                            : com.bizkredit.monitoring.enums.WatchlistCategory.SMA_0;
                    java.util.Map<String, Object> row = new java.util.HashMap<>();
                    row.put("facilityId", entry.getKey());
                    row.put("breachedCovenantCount", entry.getValue().size());
                    row.put("watchlistCategory", category.name());
                    return row;
                })
                .sorted((a, b) -> ((String) b.get("watchlistCategory")).compareTo((String) a.get("watchlistCategory")))
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public EarlyWarningSignal createEWS(Long facilityId, EarlyWarningSignal signal) {
        collateralGateway.getFacility(facilityId); // validates the facility exists

        signal.setFacilityId(facilityId);
        signal.setDetectedDate(LocalDate.now());
        signal.setStatus(EWSStatus.OPEN);

        EarlyWarningSignal saved = ewsRepository.save(signal);

        // BP2-24 AC - notify RM on new Red-severity EWS signal.
        if (saved.getSeverity() == com.bizkredit.monitoring.enums.EWSSeverity.RED) {
            notifyRole("RELATIONSHIP_MANAGER",
                    "New Red-severity EWS signal (" + saved.getSignalType() + ") on facility #" + facilityId,
                    NotificationCategory.EWS);
        }

        return saved;
    }

    @Transactional(readOnly = true)
    public List<EarlyWarningSignal> getEWSByFacility(Long facilityId) {
        return ewsRepository.findByFacilityId(facilityId);
    }

    @Transactional
    public Notification createNotification(Long userId, Notification notification) {
        var body = authServiceClient.getUser(userId);
        if (body == null || body.getData() == null) {
            throw new ResourceNotFoundException("User not found");
        }

        notification.setUserId(userId);
        notification.setStatus(NotificationStatus.UNREAD);
        notification.setCreatedDate(LocalDate.now());

        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> getNotificationsFiltered(
            Long userId,
            NotificationCategory category,
            NotificationStatus status) {

        return notificationRepository.findByUserId(userId)
                .stream()
                .filter(n -> category == null || n.getCategory() == category)
                .filter(n -> status == null || n.getStatus() == status)
                .toList();
    }

    @Transactional
    public Notification markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        notification.setStatus(NotificationStatus.READ);

        return notificationRepository.save(notification);
    }

    public Notification markAsDismissed(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        notification.setStatus(NotificationStatus.DISMISSED);

        return notificationRepository.save(notification);
    }

    // Dismissed notifications are excluded - "unread" means "still needs the
    // user's attention", and a dismissed item has already been acted on.
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.UNREAD);
    }
}
