package com.bizkredit.monitoring.client;

import com.bizkredit.monitoring.dto.ApiResponse;
import com.bizkredit.monitoring.dto.SMEBusinessDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Declarative HTTP client for sme-loan-service.
 *
 * Only used for PortfolioService.getSectorExposure(), which needs each
 * business's industry to group exposure - the one piece of data that
 * previously came from a cross-schema JOIN against bizkredit_sme_db.
 */
@FeignClient(name = "sme-loan-service", configuration = com.bizkredit.monitoring.config.FeignClientConfig.class)
public interface SmeLoanServiceClient {

    @GetMapping("/api/sme-businesses/{id}")
    ApiResponse<SMEBusinessDTO> getBusiness(@PathVariable("id") Long id);
}
