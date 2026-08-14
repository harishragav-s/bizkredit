package com.bizkredit.collateral.client;

import com.bizkredit.collateral.dto.ApiResponse;
import com.bizkredit.collateral.dto.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;


@FeignClient(name = "monitoring-service", configuration = com.bizkredit.collateral.config.FeignClientConfig.class)
public interface MonitoringServiceClient {

    @PostMapping("/api/notifications")
    ApiResponse<Object> createNotification(@RequestParam("userId") Long userId,
                                            @RequestBody NotificationRequest request);
}
