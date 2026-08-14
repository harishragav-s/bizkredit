package com.bizkredit.sme.entity;

import com.bizkredit.sme.enums.DocumentType;
import com.bizkredit.sme.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "application_document")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"application", "business"})
public class ApplicationDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long docId;

    // Nullable now - a document attaches EITHER to a loan application
    // (financial docs uploaded during/for an application) OR directly
    // to a business (KYC documents uploaded before any application
    // exists). KYC-before-application requires business-level
    // documents, so application can no longer be mandatory.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = true)
    private LoanApplication application;

    // Business a document belongs to. Always set for KYC documents;
    // also derivable for application documents via
    // application.business, but stored directly here so business-level
    // document queries don't depend on an application existing.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = true)
    private SMEBusiness business;

    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    private String financialYear;

    // Path on the server's local disk where the actual file bytes are
    // stored (see FileStorageService) - not user-editable; set only by
    // the upload endpoint. originalFileName is what the applicant's
    // browser called the file, kept separately since filePath is a
    // generated, collision-safe name on disk.
    private String filePath;
    private String originalFileName;
    private Long fileSizeBytes;

    @Builder.Default
    private LocalDate uploadedDate = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;
}
