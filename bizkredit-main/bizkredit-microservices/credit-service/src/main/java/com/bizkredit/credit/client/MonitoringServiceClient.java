package com.bizkredit.credit.client;

import com.bizkredit.credit.dto.ApiResponse;
import com.bizkredit.credit.dto.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Declarative HTTP client for monitoring-service.
 *
 * Only used to create real notification rows in monitoring-service's own
 * database. Previously NotificationHelper wrote directly to a local
 * "notification" table inside bizkredit_credit_db - which looks correct at
 * a glance (same table name, same shape) but is a completely different
 * physical database from bizkredit_monitoring_db, which is what the
 * frontend's notification bell actually reads from. So every notification
 * raised from this service was being saved somewhere nobody ever queries.
 */
@FeignClient(name = "monitoring-service", configuration = com.bizkredit.credit.config.FeignClientConfig.class)
public interface MonitoringServiceClient {

    @PostMapping("/api/notifications")
    ApiResponse<Object> createNotification(@RequestParam("userId") Long userId,
                                            @RequestBody NotificationRequest request);
}
