package com.bizkredit.collateral.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

// Local, read-only mapping of the underwriting_decision table, owned
// and written by credit-service. collateral-service reads this so
// facility creation can enforce that the sanctioned limit never
// exceeds what an Underwriting Manager actually approved - without
// this, an RM could create a facility for any amount they typed,
// regardless of what underwriting decided, making the entire approval
// step a non-binding formality.
@Entity
@Table(name = "underwriting_decision", schema = "bizkredit_credit_db")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "proposal")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class UnderwritingDecision {

    @Id
    private Long decisionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id", nullable = false)
    private CreditProposal proposal;

    private Long managerId;

    private BigDecimal sanctionedAmount;

    private BigDecimal approvedRate;

    private Integer tenure;

    @Column(length = 1000)
    private String specialConditions;

    private LocalDate decisionDate;

    // Plain String, not a local enum - collateral-service only ever
    // displays this value, it doesn't branch logic on it, so mirroring
    // credit-service's DecisionStatus enum here would be unnecessary
    // duplication for no real benefit.
    private String status;
}
