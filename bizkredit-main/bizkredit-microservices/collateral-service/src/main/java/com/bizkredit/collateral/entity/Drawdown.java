package com.bizkredit.collateral.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bizkredit.collateral.enums.DrawdownStatus;
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
@Table(name = "drawdown")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "facility")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Drawdown {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long drawdownId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "facility_id", nullable = false)
    private FacilityAccount facility;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String purpose;

    @Builder.Default
    private LocalDate requestDate = LocalDate.now();

    private LocalDate disbursedDate;

    private LocalDate repaymentDate;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DrawdownStatus status = DrawdownStatus.REQUESTED;
}
