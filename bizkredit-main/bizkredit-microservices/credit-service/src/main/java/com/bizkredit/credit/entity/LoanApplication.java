package com.bizkredit.credit.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bizkredit.credit.enums.ApplicationStatus;
import com.bizkredit.credit.enums.ProductType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Local mapping of the loan_application table, owned and written by
// sme-loan-service. credit-service reads this table to attach financial
// statements/proposals to an application and to resolve the application's
// business (for scorecard field lookups) - it never creates new applications.
@Entity
@Table(name = "loan_application", schema = "bizkredit_sme_db")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "business")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long applicationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "business_id", nullable = false)
    private SMEBusiness business;

    @Enumerated(EnumType.STRING)
    private ProductType productType;

    private BigDecimal requestedAmount;

    private Integer tenure;

    private String purpose;

    @Builder.Default
    private LocalDate applicationDate = LocalDate.now();

    private Long assignedAnalystId;

    // Mirror of the column added in sme-loan-service - lets this
    // service notify the owning applicant when a decision is made.
    private Long applicantUserId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    private Long renewedFromFacilityId;
}
