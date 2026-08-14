package com.bizkredit.credit.controller;

import com.bizkredit.credit.dto.ApiResponse;
import com.bizkredit.credit.dto.MakerCheckerDTOs.MakerCheckerRequest;
import com.bizkredit.credit.entity.MakerCheckerRecord;
import com.bizkredit.credit.service.MakerCheckerService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


@Tag(name = "Credit Analysis & Scorecard")
@RestController
@RequestMapping("/api/credit-maker-checker")
@RequiredArgsConstructor
public class MakerCheckerController {

    private final MakerCheckerService service;

    @PostMapping
    @PreAuthorize("hasAnyRole('CREDIT_ANALYST','UNDERWRITING_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<MakerCheckerRecord>> submit(
            @Valid @RequestBody MakerCheckerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Submitted for approval", service.submit(request)));
    }
}
