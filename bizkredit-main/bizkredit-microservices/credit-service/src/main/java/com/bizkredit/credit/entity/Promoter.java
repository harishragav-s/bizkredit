package com.bizkredit.credit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

// Local mapping of the promoter table, owned and written by sme-loan-service.
// credit-service only reads this table (ScoringFieldResolver's Promoter field lookups).
@Entity
@Table(name = "promoter", schema = "bizkredit_sme_db")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "business")
public class Promoter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long promoterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private SMEBusiness business;

    private String name;

    private String nationalIdRef;
    private BigDecimal shareholdingPercent;
    private BigDecimal personalNetWorth;
    private Integer creditScore;

    @Builder.Default
    private String status = "Active";
}
