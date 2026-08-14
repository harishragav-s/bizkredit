package com.bizkredit.collateral.client;

import com.bizkredit.collateral.dto.ApiResponse;
import com.bizkredit.collateral.dto.LoanApplicationDTO;
import com.bizkredit.collateral.dto.SMEBusinessDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;


@FeignClient(name = "sme-loan-service", configuration = com.bizkredit.collateral.config.FeignClientConfig.class)
public interface SmeLoanServiceClient {

    @GetMapping("/api/loan-applications/{id}")
    ApiResponse<LoanApplicationDTO> getApplication(@PathVariable("id") Long id);

    @GetMapping("/api/sme-businesses/{id}")
    ApiResponse<SMEBusinessDTO> getBusiness(@PathVariable("id") Long id);


    @PostMapping("/api/loan-applications/{id}/status")
    ApiResponse<LoanApplicationDTO> updateApplicationStatus(@PathVariable("id") Long id,
                                                             @RequestParam("value") String status);

    @PostMapping("/api/loan-applications")
    ApiResponse<LoanApplicationDTO> createApplication(@RequestParam("businessId") Long businessId,
                                                       @RequestBody Map<String, Object> application);
}
