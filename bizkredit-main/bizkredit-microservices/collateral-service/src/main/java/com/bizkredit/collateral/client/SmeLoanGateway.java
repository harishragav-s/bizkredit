package com.bizkredit.collateral.client;

import com.bizkredit.collateral.dto.LoanApplicationDTO;
import com.bizkredit.collateral.dto.SMEBusinessDTO;
import com.bizkredit.collateral.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class SmeLoanGateway {

    private final SmeLoanServiceClient client;

    public LoanApplicationDTO getApplication(Long applicationId) {
        try {
            var body = client.getApplication(applicationId);
            if (body == null || body.getData() == null) {
                throw new ResourceNotFoundException("Application not found: " + applicationId);
            }
            return body.getData();
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("sme-loan-service call failed for application {}: {}", applicationId, e.getMessage());
            throw new IllegalStateException(
                    "Could not reach sme-loan-service to load application " + applicationId, e);
        }
    }

    public SMEBusinessDTO getBusiness(Long businessId) {
        try {
            var body = client.getBusiness(businessId);
            if (body == null || body.getData() == null) {
                throw new ResourceNotFoundException("Business not found: " + businessId);
            }
            return body.getData();
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("sme-loan-service call failed for business {}: {}", businessId, e.getMessage());
            throw new IllegalStateException(
                    "Could not reach sme-loan-service to load business " + businessId, e);
        }
    }

    /**
     * Best-effort status update - the local transition (e.g. facility's
     * disbursedAmount) has already been committed by the time this runs,
     * so a genuine connectivity failure here must not roll that back - that
     * case is still logged and swallowed. A 4xx means sme-loan-service
     * actively rejected the transition for a real reason (invalid status
     * transition, etc.) - that's a genuine problem, not a transient outage,
     * so it now surfaces as a real exception instead of vanishing silently.
     */
    /**
     * BP2-45/54 - creates the renewal LoanApplication via sme-loan-service.
     * Unlike updateApplicationStatus, a failure here must NOT be silently
     * swallowed: if the renewal application can't be created, the renewal
     * simply hasn't happened and the caller needs to know immediately,
     * rather than getting a false "renewal initiated" response.
     */
    public LoanApplicationDTO createApplication(Long businessId, java.util.Map<String, Object> application) {
        try {
            var body = client.createApplication(businessId, application);
            if (body == null || body.getData() == null) {
                throw new IllegalStateException("sme-loan-service returned no data for the renewal application");
            }
            return body.getData();
        } catch (Exception e) {
            log.error("Could not create renewal application for business {}: {}", businessId, e.getMessage());
            throw new IllegalStateException(
                    "Could not create the renewal application via sme-loan-service: " + e.getMessage(), e);
        }
    }

    /**
     * Advances the application's status in sme-loan-service (SANCTIONED ->
     * DISBURSED when the RM disburses a drawdown).
     *
     * Uses Feign (SmeLoanServiceClient) - see credit-service's SmeGateway
     * for the full explanation of why this works now: the client declares
     * this as @PostMapping rather than @PatchMapping, and the receiving
     * endpoint accepts both verbs. Feign's HTTP client could never
     * reliably execute PATCH; POST has no such limitation.
     */
    public void updateApplicationStatus(Long applicationId, String status) {
        try {
            client.updateApplicationStatus(applicationId, status);
            log.info("Application {} advanced to {}", applicationId, status);
        } catch (feign.FeignException e) {
            if (e.status() >= 400 && e.status() < 500) {
                log.error("sme-loan-service rejected status update for application {} to {}: {}",
                        applicationId, status, e.getMessage());
                throw new IllegalStateException(
                        "Could not advance application " + applicationId + " to " + status
                                + " - sme-loan-service rejected the request: " + e.getMessage(), e);
            }
            log.warn("Could not update application {} to {}: {}", applicationId, status, e.getMessage());
        } catch (Exception e) {
            log.warn("Could not update application {} to {}: {}", applicationId, status, e.getMessage());
        }
    }
}
