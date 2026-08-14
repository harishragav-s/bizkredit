package com.bizkredit.monitoring.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs automatically every morning, so an RM who forgot to log this
// quarter's/month's covenant compliance gets reminded without anyone
// needing to remember to check manually - mirrors
// NPAClassificationScheduler's exact pattern. The manual trigger
// (POST /api/covenants/check-due) still exists too, for demoing the
// effect immediately rather than waiting for the scheduled time.
@Slf4j
@Component
@RequiredArgsConstructor
public class CovenantDueScheduler {

    private final CovenantNotificationService covenantNotificationService;

    // Runs once a day at 7 AM server time - covenant reviews are
    // monthly/quarterly/annual at the fastest, so a daily check is more
    // than sufficient to catch anything that crossed its due date since
    // the last run; no need for anything more frequent.
    @Scheduled(cron = "0 0 7 * * *")
    public void runDailyOverdueCheck() {
        log.info("Running scheduled covenant tracking overdue check");
        int overdue = covenantNotificationService.checkOverdueTracking();
        log.info("Scheduled covenant overdue check complete - {} covenant(s) flagged overdue", overdue);
    }
}
