package com.bizkredit.sme.service;

import com.bizkredit.sme.entity.SMEBusiness;
import com.bizkredit.sme.entity.LoanApplication;
import com.bizkredit.sme.entity.Promoter;
import com.bizkredit.sme.entity.GroupCompany;
import com.bizkredit.sme.entity.ApplicationDocument;
import com.bizkredit.sme.repository.SMEBusinessRepository;
import com.bizkredit.sme.repository.LoanApplicationRepository;
import com.bizkredit.sme.repository.PromoterRepository;
import com.bizkredit.sme.repository.GroupCompanyRepository;
import com.bizkredit.sme.repository.ApplicationDocumentRepository;
import com.bizkredit.sme.enums.ApplicationStatus;
import com.bizkredit.sme.enums.DocumentType;
import com.bizkredit.sme.enums.NotificationCategory;
import com.bizkredit.sme.enums.ProductType;
import com.bizkredit.sme.enums.VerificationStatus;
import com.bizkredit.sme.exception.BadRequestException;
import com.bizkredit.sme.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SMELoanService {

    private final SMEBusinessRepository businessRepository;
    private final PromoterRepository promoterRepository;
    private final GroupCompanyRepository groupCompanyRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final ApplicationDocumentRepository documentRepository;
    private final AuditLogService auditLogService;
    private final NotificationHelper notificationHelper;
    private final FileStorageService fileStorageService;

    // 4.2 SME Business

    @Transactional
    public SMEBusiness registerBusiness(SMEBusiness business) {
        if (businessRepository.existsByRegistrationNumber(business.getRegistrationNumber())) {
            throw new BadRequestException("Business already registered: " + business.getRegistrationNumber());
        }
        SMEBusiness saved = businessRepository.save(business);
        log.info("Business registered: {} [{}]", saved.getBusinessName(), saved.getRegistrationNumber());
        auditLogService.log(null, "CREATE", "SMEBusiness", String.valueOf(saved.getBusinessId()));

        // Notify every Admin that a new business is waiting on KYC review -
        // previously nothing notified anyone here, so a freshly registered
        // business (sitting at KYC status "Pending" by default) could go
        // unnoticed indefinitely until an Admin happened to check manually.
        notificationHelper.notifyRole("ADMIN",
                "Business \"" + saved.getBusinessName() + "\" registered and awaiting KYC verification",
                NotificationCategory.APPLICATION);

        return saved;
    }

    @Transactional(readOnly = true)
    public SMEBusiness getBusinessById(Long businessId) {
        return businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found: " + businessId));
    }

    @Transactional(readOnly = true)
    public List<SMEBusiness> getAllBusinesses() {
        return businessRepository.findAll();
    }

    // The businesses registered by a specific applicant - powers the
    // applicant's own business/KYC/apply flows without needing to go
    // through their applications.
    @Transactional(readOnly = true)
    public List<SMEBusiness> getMyBusinesses(Long applicantUserId) {
        return businessRepository.findByApplicantUserId(applicantUserId);
    }

    // GET /api/businesses with optional filters
    @Transactional(readOnly = true)
    public List<SMEBusiness> getBusinessesFiltered(String entityType, String industry, String status) {
        List<SMEBusiness> all = businessRepository.findAll();
        return all.stream()
                .filter(b -> entityType == null || (b.getEntityType() != null && b.getEntityType().name().equals(entityType)))
                .filter(b -> industry == null || industry.equalsIgnoreCase(b.getIndustry()))
                .filter(b -> status == null || status.equalsIgnoreCase(b.getStatus()))
                .toList();
    }

    // PUT /api/businesses/{id} - update profile
    @Transactional
    public SMEBusiness updateBusiness(Long businessId, SMEBusiness updates) {
        SMEBusiness existing = getBusinessById(businessId);

        // Only update non-null fields
        if (updates.getBusinessName() != null) existing.setBusinessName(updates.getBusinessName());
        if (updates.getIndustry() != null) existing.setIndustry(updates.getIndustry());
        if (updates.getAnnualTurnover() != null) existing.setAnnualTurnover(updates.getAnnualTurnover());
        if (updates.getEmployeeCount() != null) existing.setEmployeeCount(updates.getEmployeeCount());
        if (updates.getYearsInOperation() != null) existing.setYearsInOperation(updates.getYearsInOperation());
        if (updates.getPrimaryBankId() != null) existing.setPrimaryBankId(updates.getPrimaryBankId());
        // Bank details added for disbursement flow — applicant fills, RM reads
        if (updates.getBeneficiaryName() != null) existing.setBeneficiaryName(updates.getBeneficiaryName());
        if (updates.getBeneficiaryAccountNo() != null) existing.setBeneficiaryAccountNo(updates.getBeneficiaryAccountNo());
        if (updates.getBeneficiaryIfsc() != null) existing.setBeneficiaryIfsc(updates.getBeneficiaryIfsc());
        if (updates.getBeneficiaryBankName() != null) existing.setBeneficiaryBankName(updates.getBeneficiaryBankName());

        SMEBusiness saved = businessRepository.save(existing);
        auditLogService.log(null, "UPDATE", "SMEBusiness", String.valueOf(businessId));
        log.info("Business {} updated", businessId);
        return saved;
    }

    // PATCH /api/businesses/{id}/status
    @Transactional
    public SMEBusiness updateBusinessStatus(Long businessId, String status) {
        SMEBusiness business = getBusinessById(businessId);
        String validStatus = switch (status) {
            case "Active", "Inactive", "Blacklisted" -> status;
            default -> throw new BadRequestException("Invalid status. Must be Active, Inactive, or Blacklisted");
        };
        business.setStatus(validStatus);
        auditLogService.log(null, "STATUS_CHANGE", "SMEBusiness", String.valueOf(businessId));
        return businessRepository.save(business);
    }

    // Documents required for KYC verification - proof the business
    // legally exists and can be identified: a business PAN card, GST
    // returns, and a financial statement (audited financials). Checked
    // across documents attached directly to the business AND any of its
    // applications, since KYC is a business-level gate, not tied to one
    // specific loan application.
    private static final java.util.Set<DocumentType> REQUIRED_KYC_DOCUMENTS =
            java.util.Set.of(DocumentType.PAN_CARD, DocumentType.GST_RETURNS, DocumentType.AUDITED_FINANCIALS);

    @Transactional
    public SMEBusiness updateKycStatus(Long businessId, String kycStatus, String remarks) {
        SMEBusiness business = getBusinessById(businessId);
        String validStatus = switch (kycStatus) {
            case "Pending", "Verified", "Rejected" -> kycStatus;
            default -> throw new BadRequestException("Invalid KYC status: " + kycStatus);
        };

        if (validStatus.equals("Verified")) {
            List<ApplicationDocument> documents = documentRepository.findAllForBusiness(businessId);
            java.util.Set<DocumentType> uploadedTypes = documents.stream()
                    .map(ApplicationDocument::getDocumentType)
                    .collect(java.util.stream.Collectors.toSet());

            java.util.Set<DocumentType> missing = new java.util.HashSet<>(REQUIRED_KYC_DOCUMENTS);
            missing.removeAll(uploadedTypes);

            if (!missing.isEmpty()) {
                throw new BadRequestException(
                        "Cannot verify KYC - missing required documents: " + missing
                                + ". Upload these on any application for this business first.");
            }
        }

        business.setKycStatus(validStatus);
        business.setKycRemarks(validStatus.equals("Rejected") ? remarks : null);
        auditLogService.log(null, "UPDATE", "SMEBusiness", String.valueOf(businessId));
        log.info("KYC status updated for business {}: {}", businessId, kycStatus);

        // Notify the applicant who owns this business of the KYC outcome
        // - this was a major missing notification, since KYC verification
        // is what unlocks their ability to submit applications.
        if (business.getApplicantUserId() != null && !validStatus.equals("Pending")) {
            String msg = validStatus.equals("Verified")
                    ? "Your business \"" + business.getBusinessName() + "\" is now KYC-verified. You can submit loan applications."
                    : "Your business \"" + business.getBusinessName() + "\" KYC was rejected"
                        + (remarks != null && !remarks.isBlank() ? ": " + remarks : "") + ". Please re-upload documents.";
            notificationHelper.notify(business.getApplicantUserId(), msg, NotificationCategory.APPLICATION);
        }
        return businessRepository.save(business);
    }

    @Transactional
    public Promoter addPromoter(Long businessId, Promoter promoter) {
        SMEBusiness business = getBusinessById(businessId);
        promoter.setBusiness(business);
        Promoter saved = promoterRepository.save(promoter);
        auditLogService.log(null, "CREATE", "Promoter", String.valueOf(saved.getPromoterId()));
        log.info("Promoter {} added to business {}", saved.getName(), businessId);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Promoter> getPromotersByBusiness(Long businessId) {
        getBusinessById(businessId);
        return promoterRepository.findByBusiness_BusinessId(businessId);
    }

    @Transactional
    public GroupCompany linkGroupCompany(Long parentId, Long subsidiaryId, String relationship) {
        if (parentId.equals(subsidiaryId)) {
            throw new BadRequestException("Parent and subsidiary cannot be the same business");
        }
        SMEBusiness parent = getBusinessById(parentId);
        SMEBusiness subsidiary = getBusinessById(subsidiaryId);
        GroupCompany link = GroupCompany.builder()
                .parentBusiness(parent)
                .subsidiaryBusiness(subsidiary)
                .relationship(relationship)
                .build();
        log.info("Group link: {} -> {} [{}]", parentId, subsidiaryId, relationship);
        return groupCompanyRepository.save(link);
    }

    @Transactional(readOnly = true)
    public List<GroupCompany> getGroupCompaniesByBusiness(Long businessId) {
        return groupCompanyRepository.findByParentBusiness_BusinessId(businessId);
    }

    // ── 4.3 Loan Application ──────────────────────────────────────

    // Creates application in DRAFT status
    @Transactional
    public LoanApplication createApplication(Long businessId, LoanApplication application) {
        SMEBusiness business = getBusinessById(businessId);
        application.setBusiness(business);
        application.setStatus(ApplicationStatus.DRAFT);
        application.setApplicationDate(LocalDate.now());
        LoanApplication saved = loanApplicationRepository.save(application);
        auditLogService.log(null, "CREATE", "LoanApplication", String.valueOf(saved.getApplicationId()));
        log.info("Application created for business {}: id={}", businessId, saved.getApplicationId());
        return saved;
    }

    // Legacy submit - kept for backward compat, delegates to createApplication
    @Transactional
    public LoanApplication submitApplication(Long businessId, LoanApplication application) {
        SMEBusiness business = getBusinessById(businessId);
        application.setBusiness(business);
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setApplicationDate(LocalDate.now());
        LoanApplication saved = loanApplicationRepository.save(application);
        auditLogService.log(null, "CREATE", "LoanApplication", String.valueOf(saved.getApplicationId()));
        return saved;
    }

    // PATCH /api/applications/{id}/submit - explicit Draft → Submitted transition
    @Transactional
    public LoanApplication submitDraftApplication(Long applicationId) {
        LoanApplication app = getApplicationById(applicationId);
        if (app.getStatus() != ApplicationStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT applications can be submitted");
        }

        // The real KYC gate. Checked here, at submission, not at
        // creation - an applicant needs to be able to create a DRAFT
        // application first, since that's what lets them upload the
        // very documents KYC verification requires. Blocking creation
        // itself would mean nobody could ever get their first
        // application off the ground at all.
        String kycStatus = app.getBusiness().getKycStatus();
        if (!"Verified".equals(kycStatus)) {
            throw new BadRequestException(
                    "Cannot submit - KYC for this business is not yet verified (status: " + kycStatus
                            + "). An Admin must verify KYC before this application can be submitted for review.");
        }

        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setApplicationDate(LocalDate.now());
        auditLogService.log(null, "STATUS_CHANGE", "LoanApplication", String.valueOf(applicationId));
        log.info("Application {} submitted", applicationId);
        LoanApplication saved = loanApplicationRepository.save(app);

        // Notify every CREDIT_ANALYST that a new application is ready for
        // analysis. This sets status directly rather than going through
        // updateStatus() (which already has this exact broadcast for its
        // SUBMITTED case), so without this call here it would silently
        // never fire - same gap assignAnalyst() has for its own transition.
        notificationHelper.notifyRole("CREDIT_ANALYST",
                "New application #" + applicationId + " submitted and awaiting analysis",
                NotificationCategory.APPLICATION);

        return saved;
    }

    // PUT /api/applications/{id} - update (Draft only)
    @Transactional
    public LoanApplication updateApplication(Long applicationId, LoanApplication updates) {
        LoanApplication existing = getApplicationById(applicationId);
        if (existing.getStatus() != ApplicationStatus.DRAFT) {
            throw new BadRequestException("Only DRAFT applications can be updated");
        }
        if (updates.getProductType() != null) existing.setProductType(updates.getProductType());
        if (updates.getRequestedAmount() != null) existing.setRequestedAmount(updates.getRequestedAmount());
        if (updates.getTenure() != null) existing.setTenure(updates.getTenure());
        if (updates.getPurpose() != null) existing.setPurpose(updates.getPurpose());
        auditLogService.log(null, "UPDATE", "LoanApplication", String.valueOf(applicationId));
        return loanApplicationRepository.save(existing);
    }

    @Transactional(readOnly = true)
    public LoanApplication getApplicationById(Long applicationId) {
        return loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
    }

    @Transactional(readOnly = true)
    public List<LoanApplication> getApplicationsByBusiness(Long businessId) {
        return loanApplicationRepository.findByBusiness_BusinessId(businessId);
    }

    // GET /api/applications with filters
    @Transactional(readOnly = true)
    public List<LoanApplication> getApplicationsFiltered(Long businessId, ApplicationStatus status, ProductType productType, Long applicantUserId) {
        return loanApplicationRepository.findWithFilters(businessId, status, productType, applicantUserId);
    }

    @Transactional
    public LoanApplication assignAnalyst(Long applicationId, Long analystId) {
        LoanApplication application = getApplicationById(applicationId);
        application.setAssignedAnalystId(analystId);
        application.setStatus(ApplicationStatus.IN_REVIEW);
        auditLogService.log(null, "UPDATE", "LoanApplication", String.valueOf(applicationId));
        log.info("Analyst {} assigned to application {}", analystId, applicationId);
        return loanApplicationRepository.save(application);
    }

    // Enforces status workflow transitions
    @Transactional
    public LoanApplication updateStatus(Long applicationId, ApplicationStatus newStatus) {
        LoanApplication application = getApplicationById(applicationId);
        validateStatusTransition(application.getStatus(), newStatus);
        application.setStatus(newStatus);
        auditLogService.log(null, "STATUS_CHANGE", "LoanApplication", String.valueOf(applicationId));
        // Notify analyst if status changes beyond submitted
        if (application.getAssignedAnalystId() != null) {
            notificationHelper.notify(application.getAssignedAnalystId(),
                    "Application #" + applicationId + " status changed to " + newStatus,
                    NotificationCategory.APPLICATION);
        }
        // Notify the applicant who owns this application - they're the
        // one most wanting to know their application moved forward
        // (approved, sanctioned) or was rejected.
        if (application.getApplicantUserId() != null) {
            notificationHelper.notify(application.getApplicantUserId(),
                    "Your application #" + applicationId + " is now " + newStatus.name().replace('_', ' '),
                    NotificationCategory.APPLICATION);
        }
        // Notify the NEXT role in the pipeline that their step is due.
        // These roles have no assigned user on the application, so we
        // broadcast to everyone holding the role.
        switch (newStatus) {
            case SUBMITTED -> notificationHelper.notifyRole("CREDIT_ANALYST",
                    "New application #" + applicationId + " submitted and awaiting analysis",
                    NotificationCategory.APPLICATION);
            case UNDERWRITING_APPROVAL -> notificationHelper.notifyRole("UNDERWRITING_MANAGER",
                    "Application #" + applicationId + " is ready for an underwriting decision",
                    NotificationCategory.APPLICATION);
            case SANCTIONED -> notificationHelper.notifyRole("RELATIONSHIP_MANAGER",
                    "Application #" + applicationId + " is sanctioned and ready for facility setup",
                    NotificationCategory.APPLICATION);
            default -> { /* no downstream role to notify for other statuses */ }
        }
        return loanApplicationRepository.save(application);
    }

    private void validateStatusTransition(ApplicationStatus current, ApplicationStatus next) {
        boolean valid = switch (current) {
            case DRAFT -> next == ApplicationStatus.SUBMITTED;
            // SUBMITTED -> UNDERWRITING_APPROVAL is allowed directly, not just
            // via IN_REVIEW. This was the actual cause of applications getting
            // stuck showing "in review" forever with the Underwriting Manager
            // never notified and their dashboard staying empty: IN_REVIEW was
            // ONLY ever set by assignAnalyst(), which is called from a single
            // spot in the frontend (FinancialEntry.jsx, wrapped in a silent
            // try/catch) - and only if the analyst happened to open that page
            // and select the application there first. An analyst going
            // straight to the proposal screen (a completely normal path -
            // nothing in the UI requires visiting FinancialEntry first) meant
            // the application never reached IN_REVIEW. Then
            // FinancialAnalysisService.submitProposal() tried to advance
            // SUBMITTED -> UNDERWRITING_APPROVAL directly, which this method
            // rejected as an invalid transition (400), which credit-service
            // caught and swallowed into a response-message warning nobody
            // reliably sees - the application silently never left SUBMITTED,
            // and the UNDERWRITING_MANAGER notifyRole() call below never ran
            // because the whole switch statement is never reached when
            // validateStatusTransition throws first.
            case SUBMITTED -> next == ApplicationStatus.IN_REVIEW
                    || next == ApplicationStatus.UNDERWRITING_APPROVAL
                    || next == ApplicationStatus.REJECTED;
            case IN_REVIEW -> next == ApplicationStatus.UNDERWRITING_APPROVAL || next == ApplicationStatus.REJECTED;
            case UNDERWRITING_APPROVAL -> next == ApplicationStatus.SANCTIONED || next == ApplicationStatus.REJECTED;
            case SANCTIONED -> next == ApplicationStatus.DISBURSED;
            case REJECTED, DISBURSED -> false;
        };
        if (!valid) {
            throw new BadRequestException("Invalid status transition: " + current + " → " + next);
        }
    }

    @Transactional
    public ApplicationDocument uploadDocument(Long applicationId, ApplicationDocument document) {
        LoanApplication application = getApplicationById(applicationId);
        document.setApplication(application);
        ApplicationDocument saved = documentRepository.save(document);
        auditLogService.log(null, "CREATE", "ApplicationDocument", String.valueOf(saved.getDocId()));
        log.info("Document uploaded for application {}: {}", applicationId, saved.getDocumentType());
        return saved;
    }

    // Real file upload - saves the actual file to disk via
    // FileStorageService and records the real path/original name/size
    // on the ApplicationDocument row, rather than accepting a
    // hand-typed file path string like the legacy uploadDocument() above.
    @Transactional
    public ApplicationDocument uploadDocumentFile(
            Long applicationId,
            DocumentType documentType,
            String financialYear,
            MultipartFile file) {

        LoanApplication application = getApplicationById(applicationId);
        var stored = fileStorageService.store(file);

        ApplicationDocument document = ApplicationDocument.builder()
                .application(application)
                .documentType(documentType)
                .financialYear(financialYear)
                .filePath(stored.filePath())
                .originalFileName(stored.originalFileName())
                .fileSizeBytes(stored.fileSizeBytes())
                .verificationStatus(VerificationStatus.PENDING)
                .build();

        ApplicationDocument saved = documentRepository.save(document);
        auditLogService.log(null, "CREATE", "ApplicationDocument", String.valueOf(saved.getDocId()));
        log.info("File uploaded for application {}: {} ({} bytes)",
                applicationId, stored.originalFileName(), stored.fileSizeBytes());
        return saved;
    }

    // Reads a previously uploaded document's file bytes back off disk,
    // for the document-download endpoint.
    @Transactional(readOnly = true)
    public byte[] getDocumentFileBytes(Long docId) {
        ApplicationDocument document = documentRepository.findByDocId(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));
        if (document.getFilePath() == null) {
            throw new BadRequestException("This document has no file attached (it was uploaded as a reference only)");
        }
        return fileStorageService.read(document.getFilePath());
    }

    // Business-level file upload - for KYC documents uploaded directly
    // to a business, before (or independent of) any loan application.
    // Same real-file storage as uploadDocumentFile, but attaches to a
    // business instead of an application.
    @Transactional
    public ApplicationDocument uploadBusinessDocumentFile(
            Long businessId,
            DocumentType documentType,
            String financialYear,
            MultipartFile file) {

        SMEBusiness business = getBusinessById(businessId);
        var stored = fileStorageService.store(file);

        ApplicationDocument document = ApplicationDocument.builder()
                .business(business)
                .documentType(documentType)
                .financialYear(financialYear)
                .filePath(stored.filePath())
                .originalFileName(stored.originalFileName())
                .fileSizeBytes(stored.fileSizeBytes())
                .verificationStatus(VerificationStatus.PENDING)
                .build();

        ApplicationDocument saved = documentRepository.save(document);
        auditLogService.log(null, "CREATE", "ApplicationDocument", String.valueOf(saved.getDocId()));
        log.info("KYC file uploaded for business {}: {} ({} bytes)",
                businessId, stored.originalFileName(), stored.fileSizeBytes());

        // This was missing entirely - the only Admin notification fired at
        // business REGISTRATION time (see registerBusiness above), before
        // any documents exist. An admin who checked then and saw nothing to
        // review had no way of knowing when documents actually showed up
        // later, since nothing notified them again at that point.
        notificationHelper.notifyRole("ADMIN",
                "KYC document (" + documentType + ") uploaded for business \"" + business.getBusinessName()
                        + "\" - ready for review",
                NotificationCategory.APPLICATION);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<ApplicationDocument> getDocumentsByApplication(Long applicationId) {
        getApplicationById(applicationId);
        return documentRepository.findByApplication_ApplicationId(applicationId);
    }

    // Used by the KYC review page (Admin) and My Business & KYC page
    // (applicant) - shows every document for this business, whether
    // attached directly to it (KYC docs) or via one of its
    // applications (financial docs).
    @Transactional(readOnly = true)
    public List<ApplicationDocument> getDocumentsByBusiness(Long businessId) {
        getBusinessById(businessId);
        return documentRepository.findAllForBusiness(businessId);
    }

    @Transactional
    public ApplicationDocument verifyDocument(Long docId, VerificationStatus status) {
        ApplicationDocument doc = documentRepository.findByDocId(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));
        doc.setVerificationStatus(status);
        auditLogService.log(null, "STATUS_CHANGE", "ApplicationDocument", String.valueOf(docId));
        log.info("Document {} status updated to {}", docId, status);
        return documentRepository.save(doc);
    }

    // PATCH /documents/{docId}/flag-deficient - flags with reason, notifies applicant
    @Transactional
    public ApplicationDocument flagDeficient(Long docId, String reason) {
        ApplicationDocument doc = documentRepository.findByDocId(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));
        doc.setVerificationStatus(VerificationStatus.DEFICIENT);
        auditLogService.log(null, "STATUS_CHANGE", "ApplicationDocument", String.valueOf(docId));
        log.info("Document {} flagged as deficient: {}", docId, reason);
        // Notify assigned analyst of the application
        if (doc.getApplication() != null && doc.getApplication().getAssignedAnalystId() != null) {
            notificationHelper.notify(doc.getApplication().getAssignedAnalystId(),
                    "Document " + doc.getDocumentType() + " flagged deficient: " + reason,
                    NotificationCategory.APPLICATION);
        }
        return documentRepository.save(doc);
    }

    // PATCH /documents/{docId}/reject
    @Transactional
    public ApplicationDocument rejectDocument(Long docId, String reason) {
        ApplicationDocument doc = documentRepository.findByDocId(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));
        doc.setVerificationStatus(VerificationStatus.REJECTED);
        auditLogService.log(null, "STATUS_CHANGE", "ApplicationDocument", String.valueOf(docId));
        log.info("Document {} rejected: {}", docId, reason);
        return documentRepository.save(doc);
    }

    // DELETE /documents/{docId} - re-upload (resets to PENDING)
    @Transactional
    public void deleteDocument(Long docId) {
        ApplicationDocument doc = documentRepository.findByDocId(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));
        if (doc.getVerificationStatus() != VerificationStatus.DEFICIENT) {
            throw new BadRequestException("Only DEFICIENT documents can be deleted for re-upload");
        }
        documentRepository.deleteById(docId);
        auditLogService.log(null, "DELETE", "ApplicationDocument", String.valueOf(docId));
        log.info("Document {} deleted for re-upload", docId);
    }

    // GET single document by ID
    @Transactional(readOnly = true)
    public ApplicationDocument getDocumentById(Long docId) {
        return documentRepository.findByDocId(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));
    }

    // UPDATE promoter
    @Transactional
    public Promoter updatePromoter(Long promoterId, Promoter updates) {
        Promoter existing = promoterRepository.findById(promoterId)
                .orElseThrow(() -> new ResourceNotFoundException("Promoter not found: " + promoterId));
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getNationalIdRef() != null) existing.setNationalIdRef(updates.getNationalIdRef());
        if (updates.getShareholdingPercent() != null) existing.setShareholdingPercent(updates.getShareholdingPercent());
        if (updates.getPersonalNetWorth() != null) existing.setPersonalNetWorth(updates.getPersonalNetWorth());
        if (updates.getCreditScore() != null) existing.setCreditScore(updates.getCreditScore());
        auditLogService.log(null, "UPDATE", "Promoter", String.valueOf(promoterId));
        return promoterRepository.save(existing);
    }

    // SOFT-DELETE promoter
    @Transactional
    public void deletePromoter(Long promoterId) {
        Promoter existing = promoterRepository.findById(promoterId)
                .orElseThrow(() -> new ResourceNotFoundException("Promoter not found: " + promoterId));
        existing.setStatus("INACTIVE");
        promoterRepository.save(existing);
        auditLogService.log(null, "DELETE", "Promoter", String.valueOf(promoterId));
        log.info("Promoter {} soft-deleted", promoterId);
    }

    // HARD-DELETE application — Admin only, restricted to REJECTED status.
    // Preserves audit trail by only allowing deletion of applications that
    // never progressed to underwriting/sanction/disbursement.
    @Transactional
    public void deleteRejectedApplication(Long applicationId) {
        LoanApplication app = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + applicationId));
        if (app.getStatus() != ApplicationStatus.REJECTED) {
            throw new BadRequestException(
                "Only REJECTED applications can be deleted. This application is: " + app.getStatus());
        }
        // Delete associated documents first (FK constraint)
        documentRepository.deleteAll(documentRepository.findByApplication_ApplicationId(applicationId));
        loanApplicationRepository.deleteById(applicationId);
        auditLogService.log(null, "DELETE", "LoanApplication", String.valueOf(applicationId));
        log.info("Rejected application {} deleted by admin", applicationId);
    }
}
