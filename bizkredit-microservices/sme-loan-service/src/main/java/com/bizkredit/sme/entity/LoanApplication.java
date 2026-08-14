package com.bizkredit.sme.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bizkredit.sme.enums.ApplicationStatus;
import com.bizkredit.sme.enums.ProductType;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "loan_application")
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

    // Exposes just the business's ID over JSON without exposing the
    // full (lazy, @JsonIgnore'd) business object - needed so the
    // applicant's own frontend pages can discover their businessId
    // from their own applications, e.g. to look up their facility via
    // GET /api/facilities?businessId=... without a heavier nested
    // payload or the lazy-loading risk a full `business` object here
    // would reintroduce.
    public Long getBusinessId() {
        return business != null ? business.getBusinessId() : null;
    }

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Product type is required")
    private ProductType productType;

    @NotNull(message = "Requested amount is required")
    @Positive(message = "Requested amount must be positive")
    private BigDecimal requestedAmount;

    @Positive(message = "Tenure must be positive")
    private Integer tenure;

    private String purpose;

    @Builder.Default
    private LocalDate applicationDate = LocalDate.now();

    private Long assignedAnalystId;

    // The applicant user who created this application. Set once at
    // creation from the logged-in user, and used to notify the right
    // applicant as their application progresses (approved, decided,
    // facility created, etc.). Applications created before this field
    // existed will have it null - they simply won't generate applicant
    // notifications until recreated.
    private Long applicantUserId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.DRAFT;

    // Set when this application is a renewal of an existing facility
    private Long renewedFromFacilityId;
}
