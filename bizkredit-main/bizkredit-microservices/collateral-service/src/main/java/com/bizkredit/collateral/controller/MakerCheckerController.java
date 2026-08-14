package com.bizkredit.collateral.controller;

import com.bizkredit.collateral.dto.ApiResponse;
import com.bizkredit.collateral.dto.MakerCheckerDTOs.MakerCheckerRequest;

import com.bizkredit.collateral.entity.MakerCheckerRecord;
import com.bizkredit.collateral.service.MakerCheckerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Facility, Disbursement & Repayment")
@RestController
@RequestMapping("/api/maker-checker")
@RequiredArgsConstructor
public class MakerCheckerController {

    private final MakerCheckerService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('CREDIT_ANALYST', 'RELATIONSHIP_MANAGER', 'COLLATERAL_EVALUATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<MakerCheckerRecord>> submit(
            @Valid @RequestBody MakerCheckerRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Submitted for approval", service.submit(request)));
    }
}
