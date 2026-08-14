package com.bizkredit.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialStatementDTO {
    private Long statementId;
    private Long applicationId;
    private String financialYear;
    private BigDecimal revenue;
    private BigDecimal ebitda;
    private BigDecimal netWorth;
    private BigDecimal currentRatio;
    private BigDecimal debtEquityRatio;
    private BigDecimal dscr;
}
