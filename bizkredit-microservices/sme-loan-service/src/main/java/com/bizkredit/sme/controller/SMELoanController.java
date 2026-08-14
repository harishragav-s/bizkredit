package com.bizkredit.sme.controller;

import com.bizkredit.sme.entity.SMEBusiness;
import com.bizkredit.sme.entity.LoanApplication;
import com.bizkredit.sme.entity.Promoter;
import com.bizkredit.sme.entity.ApplicationDocument;
import com.bizkredit.sme.dto.ApiResponse;
import com.bizkredit.sme.enums.ApplicationStatus;
import com.bizkredit.sme.enums.DocumentType;
import com.bizkredit.sme.enums.ProductType;
import com.bizkredit.sme.service.SMELoanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "SME Onboarding & Loan Origination")
@RestController
@RequiredArgsConstructor
public class SMELoanController {

    private final SMELoanService smeService;



    @PostMapping("/api/sme-businesses")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<SMEBusiness>> registerBusiness(@Valid @RequestBody SMEBusiness business) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Business registered", smeService.registerBusiness(business)));
    }

    @GetMapping("/api/sme-businesses/{id}")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','CREDIT_ANALYST','RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<SMEBusiness>> getBusiness(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Business fetched", smeService.getBusinessById(id)));
    }

    @GetMapping("/api/sme-businesses")
    @PreAuthorize("hasAnyRole('CREDIT_ANALYST','RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<List<SMEBusiness>>> getAllBusinesses(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok("Businesses fetched",
                smeService.getBusinessesFiltered(entityType, industry, status)));
    }


    @GetMapping("/api/my-businesses")
    @PreAuthorize("hasRole('SME_APPLICANT')")
    public ResponseEntity<ApiResponse<List<SMEBusiness>>> getMyBusinesses(
            @RequestParam Long applicantUserId) {
        return ResponseEntity.ok(ApiResponse.ok("Businesses fetched",
                smeService.getMyBusinesses(applicantUserId)));
    }


    @PatchMapping("/api/sme-businesses/{id}/kyc-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SMEBusiness>> updateKyc(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) String remarks) {
        return ResponseEntity.ok(ApiResponse.ok("KYC updated", smeService.updateKycStatus(id, status, remarks)));
    }

    // Documents across every application for this business - used by
    // the Admin's KYC review page, and by the applicant's own
    // My Business & KYC page to see which required documents are on
    // file.
    @GetMapping("/api/sme-businesses/{id}/documents")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','CREDIT_ANALYST','ADMIN')")
    public ResponseEntity<ApiResponse<List<ApplicationDocument>>> getDocumentsByBusiness(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Documents fetched", smeService.getDocumentsByBusiness(id)));
    }


    @PostMapping("/api/sme-businesses/{id}/promoters")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<Promoter>> addPromoter(
            @PathVariable Long id, @Valid @RequestBody Promoter promoter) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Promoter added", smeService.addPromoter(id, promoter)));
    }

    @GetMapping("/api/sme-businesses/{id}/promoters")
    @PreAuthorize("hasAnyRole('CREDIT_ANALYST','RELATIONSHIP_MANAGER','COLLATERAL_EVALUATOR','ADMIN')")
    public ResponseEntity<ApiResponse<List<Promoter>>> getPromoters(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Promoters fetched", smeService.getPromotersByBusiness(id)));
    }

    @PostMapping("/api/loan-applications")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<LoanApplication>> createApplication(
            @RequestParam Long businessId,
            @Valid @RequestBody LoanApplication application) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Application created", smeService.createApplication(businessId, application)));
    }

    @PatchMapping("/api/loan-applications/{id}/submit")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<LoanApplication>> submitApplication(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Application submitted", smeService.submitDraftApplication(id)));
    }

    @GetMapping("/api/loan-applications/{id}")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','CREDIT_ANALYST','RELATIONSHIP_MANAGER','UNDERWRITING_MANAGER','COLLATERAL_EVALUATOR','ADMIN')")
    public ResponseEntity<ApiResponse<LoanApplication>> getApplication(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Application fetched", smeService.getApplicationById(id)));
    }

    @GetMapping("/api/loan-applications")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','CREDIT_ANALYST','RELATIONSHIP_MANAGER','UNDERWRITING_MANAGER','COLLATERAL_EVALUATOR','ADMIN')")
    public ResponseEntity<ApiResponse<List<LoanApplication>>> getApplications(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) Long applicantUserId) {
        return ResponseEntity.ok(ApiResponse.ok("Applications fetched",
                smeService.getApplicationsFiltered(businessId, status, productType, applicantUserId)));
    }


    @PatchMapping("/api/loan-applications/{id}/assign")
    @PreAuthorize("hasAnyRole('RELATIONSHIP_MANAGER','CREDIT_ANALYST','ADMIN')")
    public ResponseEntity<ApiResponse<LoanApplication>> assignAnalyst(
            @PathVariable Long id, @RequestParam Long analystId) {
        return ResponseEntity.ok(ApiResponse.ok("Analyst assigned", smeService.assignAnalyst(id, analystId)));
    }


    @RequestMapping(value = "/api/loan-applications/{id}/status", method = {RequestMethod.PATCH, RequestMethod.POST})
    @PreAuthorize("hasAnyRole('CREDIT_ANALYST','UNDERWRITING_MANAGER','RELATIONSHIP_MANAGER','ADMIN')")
    public ResponseEntity<ApiResponse<LoanApplication>> updateStatus(
            @PathVariable Long id, @RequestParam ApplicationStatus value) {
        return ResponseEntity.ok(ApiResponse.ok("Status updated", smeService.updateStatus(id, value)));
    }

    @PostMapping(value = "/api/sme-businesses/{businessId}/documents/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','ADMIN')")
    public ResponseEntity<ApiResponse<ApplicationDocument>> uploadBusinessDocumentFile(
            @PathVariable Long businessId,
            @RequestParam DocumentType documentType,
            @RequestParam(required = false, defaultValue = "") String financialYear,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("File uploaded",
                        smeService.uploadBusinessDocumentFile(businessId, documentType, financialYear, file)));
    }


    @GetMapping("/api/documents/{docId}/download")
    @PreAuthorize("hasAnyRole('SME_APPLICANT','CREDIT_ANALYST','RELATIONSHIP_MANAGER','UNDERWRITING_MANAGER','COLLATERAL_EVALUATOR','ADMIN')")
    public ResponseEntity<byte[]> downloadDocumentById(@PathVariable Long docId) {
        ApplicationDocument document = smeService.getDocumentById(docId);
        byte[] fileBytes = smeService.getDocumentFileBytes(docId);
        String filename = document.getOriginalFileName() != null
                ? document.getOriginalFileName() : "document";
        return ResponseEntity.ok()
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .header("Content-Type", detectContentType(filename))
                .body(fileBytes);
    }


    private String detectContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return "application/octet-stream";
    }


    @DeleteMapping("/api/loan-applications/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRejectedApplication(@PathVariable Long id) {
        smeService.deleteRejectedApplication(id);
        return ResponseEntity.ok(ApiResponse.ok("Rejected application deleted", null));
    }
}
