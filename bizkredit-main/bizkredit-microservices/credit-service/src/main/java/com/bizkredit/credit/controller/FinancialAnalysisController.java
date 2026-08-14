package com.bizkredit.credit.controller;

import com.bizkredit.credit.entity.FinancialStatement;
import com.bizkredit.credit.entity.CreditProposal;
import com.bizkredit.credit.entity.UnderwritingDecision;
import com.bizkredit.credit.dto.ApiResponse;
import com.bizkredit.credit.enums.ProposalStatus;
import com.bizkredit.credit.service.FinancialAnalysisService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Credit Analysis & Scorecard")
@RestController
@RequiredArgsConstructor
public class FinancialAnalysisController {

    private final FinancialAnalysisService financialService;



    @PostMapping("/api/loan-applications/{appId}/financial-statements")
    @PreAuthorize("hasAnyRole('CREDIT_ANALYST','ADMIN')")
    public ResponseEntity<ApiResponse<FinancialStatement>> addStatement(
            @PathVariable Long appId,
            @Valid @RequestBody FinancialStatement statement) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Statement added",
                        financialService.addStatement(appId, statement)));
    }

    @GetMapping("/api/loan-applications/{appId}/financial-statements")
    @PreAuthorize("hasAnyRole('CREDIT_ANALYST','UNDERWRITING_MANAGER','RELATIONSHIP_MANAGER','COLLATERAL_EVALUATOR','ADMIN')")
    public ResponseEntity<ApiResponse<List<FinancialStatement>>> getStatements(
            @PathVariable Long appId) {
        return ResponseEntity.ok(ApiResponse.ok("Statements fetched",
                financialService.getStatementsByApplication(appId)));
    }

    @PostMapping("/api/loan-applications/{appId}/credit-proposals")
    @PreAuthorize("hasAnyRole('CREDIT_ANALYST','ADMIN')")
    public ResponseEntity<ApiResponse<CreditProposal>> createProposal(
            @PathVariable Long appId,
            @Valid @RequestBody CreditProposal proposal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Proposal created",
                        financialService.createProposal(appId, proposal)));
    }

    @PatchMapping("/api/loan-applications/{appId}/credit-proposals/{id}/submit")
    @PreAuthorize("hasAnyRole('CREDIT_ANALYST','ADMIN')")
    public ResponseEntity<ApiResponse<CreditProposal>> submitProposal(
            @PathVariable Long appId, @PathVariable Long id) {
        CreditProposal saved = financialService.submitProposal(id);
        String warning = financialService.consumeStatusAdvanceWarning();
        String message = warning != null ? "WARNING: " + warning : "Proposal submitted";
        return ResponseEntity.ok(ApiResponse.ok(message, saved));
    }

    @GetMapping("/api/loan-applications/{appId}/credit-proposals")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','CREDIT_ANALYST','UNDERWRITING_MANAGER','COLLATERAL_EVALUATOR','RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<CreditProposal>>> getProposalsByStatus(
            @PathVariable Long appId,
            @RequestParam(required = false) ProposalStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Proposals fetched",
                status != null
                        ? financialService.getProposalsByStatus(status)
                        : financialService.getProposalsByApplication(appId)));
    }


    @PostMapping("/api/credit-proposals/{proposalId}/decisions")
    @PreAuthorize("hasAnyRole('UNDERWRITING_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<UnderwritingDecision>> makeDecision(
            @PathVariable Long proposalId,
            @Valid @RequestBody UnderwritingDecision decision) {
        UnderwritingDecision saved = financialService.makeDecision(proposalId, decision);
        String warning = financialService.consumeStatusAdvanceWarning();
        String message = warning != null ? "WARNING: " + warning : "Decision recorded";
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(message, saved));
    }

    @GetMapping("/api/credit-proposals/{proposalId}/decisions")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','CREDIT_ANALYST','UNDERWRITING_MANAGER','COLLATERAL_EVALUATOR','RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<UnderwritingDecision>> getDecisionByProposal(
            @PathVariable Long proposalId) {
        return ResponseEntity.ok(ApiResponse.ok("Decision fetched",
                financialService.getDecisionByProposal(proposalId)));
    }
}
