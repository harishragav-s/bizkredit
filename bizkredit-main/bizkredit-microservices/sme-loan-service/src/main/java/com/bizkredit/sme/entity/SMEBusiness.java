package com.bizkredit.sme.entity;

import com.bizkredit.sme.enums.EntityType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "sme_business")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SMEBusiness {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long businessId;

    @NotBlank(message = "Business name is required")
    private String businessName;

    @NotBlank(message = "Registration number is required")
    @Column(unique = true, nullable = false)
    private String registrationNumber;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Entity type is required")
    private EntityType entityType;

    private String industry;

    @PositiveOrZero(message = "Years in operation cannot be negative")
    private Integer yearsInOperation;

    @PositiveOrZero(message = "Annual turnover cannot be negative")
    private BigDecimal annualTurnover;

    @PositiveOrZero(message = "Employee count cannot be negative")
    private Integer employeeCount;

    private String primaryBankId;

    // Disbursement bank details — filled by applicant, read by RM for fund transfer
    private String beneficiaryName;
    private String beneficiaryAccountNo;
    private String beneficiaryIfsc;
    private String beneficiaryBankName;

    @Builder.Default
    private String kycStatus = "Pending";

    // The applicant user who registered this business. Lets us list
    // "my businesses" for an applicant directly, without going through
    // their applications - needed now that business registration
    // happens before (and independently of) any loan application.
    private Long applicantUserId;

    // Populated when kycStatus is set to Rejected - explains why to
    // the applicant, rather than leaving them with just a status word.
    @Column(length = 500)
    private String kycRemarks;

    @Builder.Default
    private String status = "Active";
}
