package com.bizkredit.monitoring.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read-only projection of collateral-service's Drawdown. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawdownDTO {
    private Long drawdownId;
    private BigDecimal amount;
    private String purpose;
    private LocalDate requestDate;
    private LocalDate disbursedDate;
    private LocalDate repaymentDate;
    private String status;
}
