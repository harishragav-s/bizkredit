package com.bizkredit.credit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Read-only projection of sme-loan-service's Promoter. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoterDTO {
    private Long promoterId;
    private Long businessId;
    private String name;
    private String pan;
    private BigDecimal shareholdingPercent;
    private BigDecimal personalNetWorth;
    private Integer creditScore;
    private String designation;
}
