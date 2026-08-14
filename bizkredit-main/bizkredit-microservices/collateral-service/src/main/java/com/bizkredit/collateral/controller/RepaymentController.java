package com.bizkredit.collateral.controller;

import com.bizkredit.collateral.dto.ApiResponse;
import com.bizkredit.collateral.entity.Repayment;
import com.bizkredit.collateral.service.RepaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Facility, Disbursement & Repayment")
@RestController
@RequestMapping("/api/repayments")
@RequiredArgsConstructor
public class RepaymentController {

    private final RepaymentService repaymentService;

    // drawdownId is a separate @RequestParam, not part of the
    // Repayment request body - Repayment.drawdown is @JsonIgnore'd
    // (both for serialization AND deserialization, which is easy to
    // miss), so a nested {"drawdown": {"drawdownId": X}} in the
    // request body was always silently discarded by Jackson, leaving
    // repayment.getDrawdown() null and causing a NullPointerException
    // every time this endpoint was actually called.
    @PostMapping
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','SME_APPLICANT','ADMIN')")
    public ResponseEntity<ApiResponse<Repayment>> recordRepayment(
            @RequestParam Long drawdownId,
            @Valid @RequestBody Repayment repayment) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Repayment recorded",
                        repaymentService.recordRepayment(drawdownId, repayment)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','CREDIT_ANALYST','SME_APPLICANT','ADMIN')")
    public ResponseEntity<ApiResponse<?>> getRepayments(
            @RequestParam(required = false) Long facilityId,
            @RequestParam(required = false) Long drawdownId) {

        if (drawdownId != null) {
            List<Repayment> repayments = repaymentService.getByDrawdown(drawdownId);
            return ResponseEntity.ok(ApiResponse.ok("Repayments fetched", repayments));
        }

        if (facilityId != null) {
            List<Repayment> repayments = repaymentService.getByFacility(facilityId);
            return ResponseEntity.ok(ApiResponse.ok("Repayments fetched", repayments));
        }

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Provide facilityId or drawdownId"));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Repayment>> verify(
            @PathVariable Long id,
            @RequestParam Long verifiedById) {

        return ResponseEntity.ok(ApiResponse.ok("Repayment verified",
                repaymentService.verifyRepayment(id, verifiedById)));
    }
}
