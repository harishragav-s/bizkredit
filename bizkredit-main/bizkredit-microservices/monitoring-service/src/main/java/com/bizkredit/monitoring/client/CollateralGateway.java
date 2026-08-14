package com.bizkredit.monitoring.client;

import com.bizkredit.monitoring.dto.DrawdownDTO;
import com.bizkredit.monitoring.dto.FacilityDTO;
import com.bizkredit.monitoring.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Thin facade over CollateralServiceClient.
 *
 * Unwraps the ApiResponse envelope and turns a missing/unreachable facility
 * into the same ResourceNotFoundException the old local repository lookups
 * used to throw.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CollateralGateway {

    private final CollateralServiceClient client;

    public List<FacilityDTO> getFacilitiesByStatus(String status) {
        try {
            var body = client.getFacilities(status);
            return (body == null || body.getData() == null) ? List.of() : body.getData();
        } catch (Exception e) {
            log.error("collateral-service call failed listing facilities (status={}): {}", status, e.getMessage());
            throw new IllegalStateException("Could not reach collateral-service to list facilities - " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public List<FacilityDTO> getAllFacilities() {
        try {
            var body = client.getFacilities(null);
            return (body == null || body.getData() == null) ? List.of() : body.getData();
        } catch (Exception e) {
            log.error("collateral-service call failed listing all facilities: {}", e.getMessage());
            throw new IllegalStateException("Could not reach collateral-service to list facilities - " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    // BP2-49/58 renewal-pipeline widget.
    public List<FacilityDTO> getExpiringFacilities(int withinDays) {
        try {
            var body = client.getExpiringFacilities(withinDays);
            return (body == null || body.getData() == null) ? List.of() : body.getData();
        } catch (Exception e) {
            log.error("collateral-service call failed listing expiring facilities: {}", e.getMessage());
            throw new IllegalStateException("Could not reach collateral-service to list expiring facilities - " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public FacilityDTO getFacility(Long facilityId) {
        try {
            var body = client.getFacility(facilityId);
            if (body == null || body.getData() == null) {
                throw new ResourceNotFoundException("Facility not found: " + facilityId);
            }
            return body.getData();
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("collateral-service call failed for facility {}: {}", facilityId, e.getMessage());
            throw new IllegalStateException("Could not reach collateral-service to load facility " + facilityId
                    + " - " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public List<DrawdownDTO> getDrawdownsByFacility(Long facilityId) {
        try {
            var body = client.getDrawdownsByFacility(facilityId);
            return (body == null || body.getData() == null) ? List.of() : body.getData();
        } catch (Exception e) {
            log.error("collateral-service call failed listing drawdowns for facility {}: {}",
                    facilityId, e.getMessage());
            throw new IllegalStateException(
                    "Could not reach collateral-service to load drawdowns for facility " + facilityId
                            + " - " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /** Best-effort - the classification algorithm's decision already stands either way. */
    public void updateNpaStatus(Long facilityId, String status) {
        try {
            client.updateNpaStatus(facilityId, status);
        } catch (Exception e) {
            log.warn("Could not update NPA status for facility {} to {}: {}", facilityId, status, e.getMessage());
        }
    }

    /** Best-effort - same reasoning as updateNpaStatus. */
    public void markDrawdownOverdue(Long facilityId, Long drawdownId) {
        try {
            client.markDrawdownOverdue(facilityId, drawdownId);
        } catch (Exception e) {
            log.warn("Could not mark drawdown {} overdue on facility {}: {}", drawdownId, facilityId, e.getMessage());
        }
    }
}
