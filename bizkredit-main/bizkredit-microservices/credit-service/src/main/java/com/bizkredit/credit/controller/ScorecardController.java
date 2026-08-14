package com.bizkredit.credit.controller;

import com.bizkredit.credit.dto.ApiResponse;
import com.bizkredit.credit.entity.ScorecardModel;
import com.bizkredit.credit.enums.ProductType;
import com.bizkredit.credit.enums.ScorecardStatus;
import com.bizkredit.credit.service.ScorecardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Credit Analysis & Scorecard")
@RestController
@RequestMapping("/api/scorecards")
@RequiredArgsConstructor
public class ScorecardController {

    private final ScorecardService scorecardService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ScorecardModel>> create(
            @Valid @RequestBody ScorecardModel scorecard,
            @RequestParam Long createdById) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Scorecard created",
                        scorecardService.createScorecard(scorecard, createdById)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CREDIT_ANALYST')")
    public ResponseEntity<ApiResponse<List<ScorecardModel>>> getAll(
            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) ScorecardStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Scorecards fetched",
                scorecardService.getScorecards(productType, status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','CREDIT_ANALYST')")
    public ResponseEntity<ApiResponse<ScorecardModel>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Scorecard fetched",
                scorecardService.getById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ScorecardModel>> update(
            @PathVariable Long id, @RequestBody ScorecardModel updates) {
        return ResponseEntity.ok(ApiResponse.ok("Scorecard updated",
                scorecardService.updateScorecard(id, updates)));
    }


    @PostMapping("/{id}/compute")
    @PreAuthorize("hasAnyRole('ADMIN','CREDIT_ANALYST')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> compute(
            @PathVariable Long id, @RequestParam Long applicationId) {
        return ResponseEntity.ok(ApiResponse.ok("Score computed",
                scorecardService.computeScore(id, applicationId)));
    }
}
