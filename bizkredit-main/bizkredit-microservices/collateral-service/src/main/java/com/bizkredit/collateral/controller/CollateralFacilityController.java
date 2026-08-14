package com.bizkredit.collateral.controller;

import com.bizkredit.collateral.entity.FacilityAccount;
import com.bizkredit.collateral.entity.CollateralRecord;
import com.bizkredit.collateral.entity.CollateralRevaluation;
import com.bizkredit.collateral.entity.Drawdown;
import com.bizkredit.collateral.dto.ApiResponse;
import com.bizkredit.collateral.enums.FacilityStatus;
import com.bizkredit.collateral.service.CollateralFacilityService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "Facility, Disbursement & Repayment")
@RestController
@RequiredArgsConstructor
public class CollateralFacilityController {

    private final CollateralFacilityService service;

    // Collateral

    @PostMapping("/api/loan-applications/{appId}/collaterals")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','COLLATERAL_EVALUATOR','ADMIN')")
    public ResponseEntity<ApiResponse<CollateralRecord>> registerCollateral(
            @PathVariable Long appId,
            @Valid @RequestBody CollateralRecord collateral) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Collateral registered",
                        service.registerCollateral(appId, collateral)));
    }

    // Lists every collateral record registered against an application
    // - previously the evaluator had no way to browse what's already
    // there, only to register something new or fetch one record if
    // they already knew its exact ID.
    @GetMapping("/api/loan-applications/{appId}/collaterals")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','COLLATERAL_EVALUATOR','CREDIT_ANALYST','ADMIN')")
    public ResponseEntity<ApiResponse<List<CollateralRecord>>> getCollateralsByApplication(
            @PathVariable Long appId) {

        return ResponseEntity.ok(ApiResponse.ok("Collaterals fetched",
                service.getCollateralByApplication(appId)));
    }

    @PostMapping("/api/loan-applications/{appId}/collaterals/{id}/evaluate")
    @PreAuthorize("hasAnyRole('COLLATERAL_EVALUATOR','ADMIN')")
    public ResponseEntity<ApiResponse<CollateralRecord>> evaluate(
            @PathVariable Long appId,
            @PathVariable Long id,
            @RequestParam BigDecimal confirmedMarketValue) {

        return ResponseEntity.ok(ApiResponse.ok("Collateral evaluated and confirmed",
                service.evaluateCollateral(id, confirmedMarketValue)));
    }

    @PostMapping("/api/loan-applications/{appId}/collaterals/{id}/revalue")
    @PreAuthorize("hasAnyRole('COLLATERAL_EVALUATOR','ADMIN')")
    public ResponseEntity<ApiResponse<CollateralRevaluation>> revalue(
            @PathVariable Long appId,
            @PathVariable Long id,
            @RequestParam BigDecimal newValue,
            @RequestParam Long revaluedById) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Collateral revalued",
                        service.revalueCollateral(id, newValue, revaluedById)));
    }

    // Facility

    @PostMapping("/api/facilities")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<FacilityAccount>> createFacility(
            @RequestParam Long applicationId,
            @RequestParam Long businessId,
            @Valid @RequestBody FacilityAccount facility) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Facility created",
                        service.createFacility(applicationId, businessId, facility)));
    }

    @GetMapping("/api/facilities/{id}")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','CREDIT_ANALYST','SME_APPLICANT','ADMIN')")
    public ResponseEntity<ApiResponse<FacilityAccount>> getFacility(
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.ok("Facility fetched",
                service.getFacilityById(id)));
    }

    // BP2-45/54 - Facility Renewal Management API

    @GetMapping("/api/facilities/expiring")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<FacilityAccount>>> getExpiringFacilities(
            @RequestParam(defaultValue = "90") int withinDays) {

        return ResponseEntity.ok(ApiResponse.ok("Facilities expiring within " + withinDays + " days",
                service.getExpiringFacilities(withinDays)));
    }

    @PostMapping("/api/facilities/{id}/renew")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<com.bizkredit.collateral.dto.LoanApplicationDTO>> renewFacility(
            @PathVariable Long id) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Renewal application initiated", service.renewFacility(id)));
    }

    @GetMapping("/api/facilities/{id}/renewal-history")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','CREDIT_ANALYST','ADMIN')")
    public ResponseEntity<ApiResponse<List<com.bizkredit.collateral.entity.LoanApplication>>> getRenewalHistory(
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.ok("Renewal history fetched", service.getRenewalHistory(id)));
    }

    // Lets an applicant look up their own facility(ies) by business ID,
    // Lets an applicant look up their own facility(ies) by business ID,
    // or lets an RM/Admin browse every facility across all businesses
    // (optionally filtered by status) when businessId is omitted -
    // both were previously impossible without already knowing a raw
    // facilityId, which made Facility Management (no way to find a
    // facility created earlier), Covenant Tracker, and EWS Board all
    // unusable beyond the exact session a facility was first created in.
    @GetMapping("/api/facilities")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','CREDIT_ANALYST','SME_APPLICANT','ADMIN')")
    public ResponseEntity<ApiResponse<List<FacilityAccount>>> getFacilities(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) FacilityStatus status) {

        List<FacilityAccount> facilities = businessId != null
                ? service.getFacilitiesByBusiness(businessId)
                : service.getAllFacilities(status);

        return ResponseEntity.ok(ApiResponse.ok("Facilities fetched", facilities));
    }

    // Closing is the real-world equivalent of "deleting" a facility -
    // a sanctioned/disbursed facility is a regulated record that never
    // gets deleted, only formally closed once fully repaid (enforced
    // in the service layer).
    @PatchMapping("/api/facilities/{id}/close")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<FacilityAccount>> closeFacility(@PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.ok("Facility closed",
                service.closeFacility(id)));
    }

    // Internal endpoint - not used by the frontend. monitoring-service's
    // NPA classification job (nightly @Scheduled batch, plus its manual
    // POST /api/npa/classify trigger) calls this over Feign to flip a
    // facility's status once IT has decided the facility crossed into or
    // out of NPA. collateral-service still owns the actual write to its
    // own facility_account table - monitoring-service is not allowed to
    // write here directly.
    @RequestMapping(value = "/api/facilities/{id}/npa-status", method = {RequestMethod.PATCH, RequestMethod.POST})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FacilityAccount>> updateNpaStatus(
            @PathVariable Long id, @RequestParam FacilityStatus status) {

        return ResponseEntity.ok(ApiResponse.ok("Facility NPA status updated",
                service.updateNpaStatus(id, status)));
    }

    // Hard delete - only succeeds if nothing has ever been disbursed
    // against this facility (see service layer). A facility with real
    // money movement must be closed via the endpoint above instead.
    @DeleteMapping("/api/facilities/{id}")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteFacility(@PathVariable Long id) {
        service.deleteFacility(id);
        return ResponseEntity.ok(ApiResponse.ok("Facility deleted", null));
    }

    // Drawdowns

    @PostMapping("/api/facilities/{facilityId}/drawdowns")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','SME_APPLICANT','ADMIN')")
    public ResponseEntity<ApiResponse<Drawdown>> requestDrawdown(
            @PathVariable Long facilityId,
            @Valid @RequestBody Drawdown drawdown) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Drawdown requested",
                        service.requestDrawdown(facilityId, drawdown)));
    }

    // Lists every drawdown against a facility, regardless of who
    // requested it - previously nothing called this, so a drawdown
    // requested by the applicant was invisible to the RM the moment
    // they selected that facility (the frontend only ever tracked
    // drawdowns it had personally just created in that same session).
    @GetMapping("/api/facilities/{facilityId}/drawdowns")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','SME_APPLICANT','CREDIT_ANALYST','ADMIN')")
    public ResponseEntity<ApiResponse<List<Drawdown>>> getDrawdowns(
            @PathVariable Long facilityId) {

        return ResponseEntity.ok(ApiResponse.ok("Drawdowns fetched",
                service.getDrawdownsByFacility(facilityId)));
    }

    @PatchMapping("/api/facilities/{facilityId}/drawdowns/{id}/disburse")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Drawdown>> disburseDrawdown(
            @PathVariable Long facilityId,
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.ok("Drawdown disbursed",
                service.disburseDrawdown(id)));
    }

    // Internal endpoint - not used by the frontend. monitoring-service's
    // NPA classification job calls this over Feign once IT has detected
    // that a drawdown's repayment date has passed with no repayment -
    // collateral-service still owns the actual write to its own
    // drawdown table.
    @RequestMapping(value = "/api/facilities/{facilityId}/drawdowns/{id}/overdue", method = {RequestMethod.PATCH, RequestMethod.POST})
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Drawdown>> markDrawdownOverdue(
            @PathVariable Long facilityId,
            @PathVariable Long id) {

        return ResponseEntity.ok(ApiResponse.ok("Drawdown marked overdue",
                service.markDrawdownOverdue(id)));
    }

}
