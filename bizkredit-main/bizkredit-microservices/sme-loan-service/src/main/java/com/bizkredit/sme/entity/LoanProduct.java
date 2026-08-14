package com.bizkredit.sme.entity;

import com.bizkredit.sme.enums.DocumentType;
import com.bizkredit.sme.enums.LoanProductStatus;
import com.bizkredit.sme.enums.ProductType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "loan_product")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class LoanProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;

    @Column(unique = true, nullable = false)
    @NotBlank(message = "Product code is required")
    private String productCode;

    @NotBlank(message = "Product name is required")
    private String productName;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Product type is required")
    private ProductType productType;

    @Positive(message = "Min amount must be positive")
    private BigDecimal minAmount;

    @Positive(message = "Max amount must be positive")
    private BigDecimal maxAmount;

    @Positive(message = "Min tenure must be positive")
    private Integer minTenure;

    @Positive(message = "Max tenure must be positive")
    private Integer maxTenure;

    private BigDecimal baseInterestRate;

    @ElementCollection(targetClass = DocumentType.class)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "loan_product_documents", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "document_type")
    @Builder.Default
    private List<DocumentType> requiredDocuments = new ArrayList<>();

    // JSON string for configurable eligibility rules
    @Column(columnDefinition = "TEXT")
    private String eligibilityCriteria;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private LoanProductStatus status = LoanProductStatus.ACTIVE;

    private Long createdById;

    @Builder.Default
    private LocalDateTime createdDate = LocalDateTime.now();
}
