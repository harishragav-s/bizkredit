package com.bizkredit.collateral.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bizkredit.collateral.enums.PaymentMethod;
import com.bizkredit.collateral.enums.RepaymentStatus;
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
@Table(name = "repayment")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"drawdown", "facility"})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Repayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long repaymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "drawdown_id", nullable = false)
    private Drawdown drawdown;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityAccount facility;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private BigDecimal principalComponent;

    private BigDecimal interestComponent;

    @Builder.Default
    private LocalDate paymentDate = LocalDate.now();

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RepaymentStatus status = RepaymentStatus.RECEIVED;

    // FK to User who recorded this repayment
    private Long recordedById;

    // FK to User who verified (for maker-checker)
    private Long verifiedById;
}
