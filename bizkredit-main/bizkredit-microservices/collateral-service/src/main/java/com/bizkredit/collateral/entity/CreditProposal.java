package com.bizkredit.collateral.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

// Local, read-only mapping of the credit_proposal table, owned and
// written by credit-service. collateral-service only needs this to
// reach UnderwritingDecision (via decision.proposal) - it never
// creates or modifies proposals itself. Kept intentionally minimal:
// only the fields actually needed for that read path, not a full
// mirror of every column on the real entity.
@Entity
@Table(name = "credit_proposal", schema = "bizkredit_credit_db")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "application")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CreditProposal {

    @Id
    private Long proposalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnore
    @JoinColumn(name = "application_id", nullable = false)
    private LoanApplication application;

    private BigDecimal suggestedAmount;
}
