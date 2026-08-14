package com.bizkredit.sme.controller;

import com.bizkredit.sme.dto.ApiResponse;
import com.bizkredit.sme.entity.LoanProduct;
import com.bizkredit.sme.enums.LoanProductStatus;
import com.bizkredit.sme.service.LoanProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "SME Onboarding & Loan Origination")
@RestController
@RequestMapping("/api/loan-products")
@RequiredArgsConstructor
public class LoanProductController {

    private final LoanProductService loanProductService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<LoanProduct>> create(
            @Valid @RequestBody LoanProduct product,
            @RequestParam Long createdById) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Loan product created",
                        loanProductService.createProduct(product, createdById)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','CREDIT_ANALYST','RELATIONSHIP_MANAGER','SME_APPLICANT')")
    public ResponseEntity<ApiResponse<List<LoanProduct>>> getAll(
            @RequestParam(required = false) LoanProductStatus status) {
        return ResponseEntity.ok(ApiResponse.ok("Loan products fetched",
                loanProductService.getProducts(status)));
    }
}
