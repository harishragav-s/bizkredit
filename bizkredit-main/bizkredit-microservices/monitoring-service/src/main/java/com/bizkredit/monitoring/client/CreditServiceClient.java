package com.bizkredit.monitoring.client;

import com.bizkredit.monitoring.dto.ApiResponse;
import com.bizkredit.monitoring.dto.FinancialStatementDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "credit-service", configuration = com.bizkredit.monitoring.config.FeignClientConfig.class)
public interface CreditServiceClient {

    @GetMapping("/api/loan-applications/{appId}/financial-statements")
    ApiResponse<List<FinancialStatementDTO>> getStatements(@PathVariable("appId") Long appId);
}
