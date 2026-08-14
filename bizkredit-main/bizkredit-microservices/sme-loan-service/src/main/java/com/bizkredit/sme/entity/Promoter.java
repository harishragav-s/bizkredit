package com.bizkredit.sme.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "promoter")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
// Exclude business from toString to prevent lazy loading issues
@ToString(exclude = "business")
public class Promoter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long promoterId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private SMEBusiness business;

    @NotBlank
    private String name;

    private String nationalIdRef;
    private BigDecimal shareholdingPercent;
    private BigDecimal personalNetWorth;
    private Integer creditScore;

    @Builder.Default
    private String status = "Active";
}
