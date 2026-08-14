package com.bizkredit.credit.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "financial_statement")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class FinancialStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long statementId;

    // The application this statement belongs to is owned by sme-loan-service -
    // credit-service stores only its id (fetched over Feign via SmeGateway when
    // the caller needs the full application), not a cross-schema JPA relation.
    // Not @NotNull: the service sets this from the URL path param after @Valid
    // has already run on the incoming request body, which never includes it.
    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @NotBlank(message = "Financial year is required")
    private String financialYear;

    @PositiveOrZero(message = "Revenue cannot be negative")
    private BigDecimal revenue;

    private BigDecimal ebitda;

    private BigDecimal pat;

    @PositiveOrZero(message = "Total assets cannot be negative")
    private BigDecimal totalAssets;

    @PositiveOrZero(message = "Total liabilities cannot be negative")
    private BigDecimal totalLiabilities;

    private BigDecimal netWorth;

    private BigDecimal currentRatio;

    private BigDecimal debtEquityRatio;

    private BigDecimal dscr;

    private Long enteredById;

    @Builder.Default
    private String status = "Draft";
}
