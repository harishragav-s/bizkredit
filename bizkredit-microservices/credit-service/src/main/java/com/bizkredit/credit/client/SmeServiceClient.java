package com.bizkredit.credit.client;

import com.bizkredit.credit.dto.ApiResponse;
import com.bizkredit.credit.dto.LoanApplicationDTO;
import com.bizkredit.credit.dto.PromoterDTO;
import com.bizkredit.credit.dto.SMEBusinessDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "sme-loan-service", configuration = com.bizkredit.credit.config.FeignClientConfig.class)
public interface SmeServiceClient {

    @GetMapping("/api/loan-applications/{id}")
    ApiResponse<LoanApplicationDTO> getApplication(@PathVariable("id") Long id);

    @GetMapping("/api/sme-businesses/{id}")
    ApiResponse<SMEBusinessDTO> getBusiness(@PathVariable("id") Long id);

    @GetMapping("/api/sme-businesses/{id}/promoters")
    ApiResponse<List<PromoterDTO>> getPromoters(@PathVariable("id") Long businessId);

    @PostMapping("/api/loan-applications/{id}/status")
    ApiResponse<LoanApplicationDTO> updateApplicationStatus(@PathVariable("id") Long id,
                                                            @RequestParam("value") String status);
}
