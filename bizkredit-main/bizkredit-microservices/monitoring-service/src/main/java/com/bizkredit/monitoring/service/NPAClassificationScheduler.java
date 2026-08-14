package com.bizkredit.monitoring.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Runs NPA classification automatically every night, so a facility
// with an overdue, unpaid drawdown gets flagged (EWS signal, or NPA
// classification once far enough overdue) without anyone needing to
// remember to click "Run Classification" on the admin page. The
// manual trigger (POST /api/npa/classify) still exists too - useful
// for demoing the effect immediately rather than waiting for the
// actual scheduled time, since backdating a drawdown's due date is
// the realistic way to exercise this in a short-lived environment.
@Slf4j
@Component
@RequiredArgsConstructor
public class NPAClassificationScheduler {

    private final NPAClassificationService npaClassificationService;

    // Runs once a day at 2 AM server time - a real bank's NPA
    // classification is a nightly batch job, not a real-time check,
    // since it only needs to catch facilities that crossed a day
    // boundary since the last run.
    @Scheduled(cron = "0 0 2 * * *")
    public void runNightlyClassification() {
        log.info("Running scheduled NPA classification job");
        int classified = npaClassificationService.runClassification();
        log.info("Scheduled NPA classification complete - {} facility(s) newly classified as NPA", classified);
    }
}
