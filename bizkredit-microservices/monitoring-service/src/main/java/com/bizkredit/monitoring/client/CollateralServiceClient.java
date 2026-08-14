package com.bizkredit.monitoring.client;

import com.bizkredit.monitoring.dto.ApiResponse;
import com.bizkredit.monitoring.dto.DrawdownDTO;
import com.bizkredit.monitoring.dto.FacilityDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Declarative HTTP client for collateral-service.
 *
 * Replaces monitoring-service's previous cross-schema JPA reads/writes of
 * bizkredit_collateral_db.facility_account / drawdown.
 */
@FeignClient(name = "collateral-service", configuration = com.bizkredit.monitoring.config.FeignClientConfig.class)
public interface CollateralServiceClient {

    @GetMapping("/api/facilities")
    ApiResponse<List<FacilityDTO>> getFacilities(@RequestParam(value = "status", required = false) String status);

    @GetMapping("/api/facilities/{id}")
    ApiResponse<FacilityDTO> getFacility(@PathVariable("id") Long id);

    // BP2-49/58 renewal-pipeline widget - reuses BP2-45/54's expiring-facilities endpoint.
    @GetMapping("/api/facilities/expiring")
    ApiResponse<List<FacilityDTO>> getExpiringFacilities(@RequestParam("withinDays") int withinDays);

    @GetMapping("/api/facilities/{facilityId}/drawdowns")
    ApiResponse<List<DrawdownDTO>> getDrawdownsByFacility(@PathVariable("facilityId") Long facilityId);

    // Internal endpoints, ADMIN-only on the receiving side - see
    // CollateralFacilityController on collateral-service. Under a real
    // user's call (the manual /api/npa/classify trigger, or an RM/ADMIN
    // action) the forwarded JWT already carries ADMIN for the classify
    // path; under the nightly @Scheduled job, FeignClientConfig attaches
    // a short-lived system token with role=ADMIN instead.
    // Sends POST, not PATCH - see credit-service's SmeServiceClient for
    // the full explanation; same fix, same reason.
    @PostMapping("/api/facilities/{id}/npa-status")
    ApiResponse<FacilityDTO> updateNpaStatus(@PathVariable("id") Long id,
                                             @RequestParam("status") String status);

    @PostMapping("/api/facilities/{facilityId}/drawdowns/{id}/overdue")
    ApiResponse<DrawdownDTO> markDrawdownOverdue(@PathVariable("facilityId") Long facilityId,
                                                  @PathVariable("id") Long drawdownId);
}
