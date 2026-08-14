package com.bizkredit.credit.entity;

import com.bizkredit.credit.enums.EntityType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Local mapping of the sme_business table, which is owned and written by
// sme-loan-service. credit-service only reads this table (via LoanApplication.business
// and ScoringFieldResolver's SMEBusiness field lookups), so this entity carries the
// full column set for compatibility but credit-service never writes new rows here.
@Entity
@Table(name = "sme_business", schema = "bizkredit_sme_db")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SMEBusiness {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long businessId;

    private String businessName;

    @Column(unique = true, nullable = false)
    private String registrationNumber;

    @Enumerated(EnumType.STRING)
    private EntityType entityType;

    private String industry;

    private Integer yearsInOperation;

    private BigDecimal annualTurnover;

    private Integer employeeCount;

    private String primaryBankId;

    @Builder.Default
    private String kycStatus = "Pending";

    @Builder.Default
    private String status = "Active";
}
