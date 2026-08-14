package com.bizkredit.collateral.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "collateral_revaluation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "collateral")
public class CollateralRevaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long revaluationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collateral_id", nullable = false)
    private CollateralRecord collateral;

    private LocalDate revaluationDate;
    private BigDecimal previousValue;
    private BigDecimal newValue;
    private Long revaluedById;
    private BigDecimal changePercent;

    @Builder.Default
    private String status = "Completed";
}
