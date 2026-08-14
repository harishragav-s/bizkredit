package com.bizkredit.sme.client;

import com.bizkredit.sme.dto.ApiResponse;
import com.bizkredit.sme.dto.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Declarative HTTP client for monitoring-service.
 *
 * Only used to create real notification rows in monitoring-service's own
 * database (bizkredit_monitoring_db). Previously NotificationHelper wrote
 * directly to a local "notification" table inside bizkredit_sme_db - a
 * different physical database from the one the frontend's notification
 * bell actually reads from, so nothing raised here ever reached the user.
 */
@FeignClient(name = "monitoring-service", configuration = com.bizkredit.sme.config.FeignClientConfig.class)
public interface MonitoringServiceClient {

    @PostMapping("/api/notifications")
    ApiResponse<Object> createNotification(@RequestParam("userId") Long userId,
                                            @RequestBody NotificationRequest request);
}
