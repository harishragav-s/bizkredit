package com.bizkredit.credit.client;

import com.bizkredit.credit.dto.LoanApplicationDTO;
import com.bizkredit.credit.dto.PromoterDTO;
import com.bizkredit.credit.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Thin facade over SmeServiceClient.
 *
 * Two jobs:
 *   1. Unwrap the ApiResponse envelope so callers see the payload directly.
 *   2. Classify failures - a genuinely missing application is a 404 for the
 *      user, whereas sme-loan-service being unreachable is an upstream
 *      outage. Collapsing both into a generic exception makes production
 *      debugging much harder.
 *
 * Keeping this separate from the @FeignClient interface means the client
 * stays a pure transport declaration.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SmeGateway {

    private final SmeServiceClient client;

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

    /** Promoters are optional scoring input - an empty list is a valid result. */
    public List<PromoterDTO> getPromoters(Long businessId) {
        if (businessId == null) return List.of();
        try {
            var body = client.getPromoters(businessId);
            return (body == null || body.getData() == null) ? List.of() : body.getData();
        } catch (Exception e) {
            log.warn("Could not load promoters for business {}: {}", businessId, e.getMessage());
            return List.of();
        }
    }

    /**
     * The SME business behind an application - fetched by ID separately, since
     * LoanApplicationDTO.business (the nested field) can never actually be
     * populated: sme-loan-service's LoanApplication.business is @JsonIgnore'd
     * on the wire (it only exposes a flat businessId getter instead), so that
     * nested field always deserializes to null. This is optional scoring
     * input like promoters, so a failure returns null rather than throwing.
     */
    public com.bizkredit.credit.dto.SMEBusinessDTO getBusiness(Long businessId) {
        if (businessId == null) return null;
        try {
            var body = client.getBusiness(businessId);
            return body == null ? null : body.getData();
        } catch (Exception e) {
            log.warn("Could not load business {}: {}", businessId, e.getMessage());
            return null;
        }
    }

    /**
     * Advances the application's status in sme-loan-service.
     *
     * Uses Feign (SmeServiceClient), like every other call in this class.
     *
     * IMPORTANT - why this works now when it kept failing before: Feign's
     * underlying HTTP client could not execute PATCH ("Invalid HTTP
     * method: PATCH" - a hard limitation of java.net.HttpURLConnection,
     * which no amount of swapping in OkHttp or setting enable-properties
     * reliably fixed). The fix is NOT a different HTTP client - it's that
     * SmeServiceClient now declares this call as @PostMapping instead of
     * @PatchMapping, and sme-loan-service's endpoint accepts BOTH PATCH
     * and POST (see SMELoanController.updateStatus). POST has never had
     * any client-support ambiguity. The frontend still calls that endpoint
     * with PATCH, unchanged - only this internal service-to-service hop
     * switched verbs.
     *
     * A 4xx means sme-loan-service actively rejected the transition (a
     * real bug/data problem) and is rethrown so it can't fail silently
     * again. Connectivity failures are logged and swallowed, since the
     * decision/proposal write that triggered this is already committed
     * and must not roll back.
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
