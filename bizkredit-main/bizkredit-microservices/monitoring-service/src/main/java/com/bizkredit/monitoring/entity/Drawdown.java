package com.bizkredit.monitoring.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.bizkredit.monitoring.enums.DrawdownStatus;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

// Local mapping of the drawdown table, owned and written by collateral-service.
// monitoring-service reads this in NPAClassificationService to find overdue
// drawdowns per facility - it never creates or updates drawdowns.
@Entity
@Table(name = "drawdown", schema = "bizkredit_collateral_db")
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
