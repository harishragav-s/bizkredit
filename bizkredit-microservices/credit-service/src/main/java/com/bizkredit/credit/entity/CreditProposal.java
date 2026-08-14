package com.bizkredit.credit.entity;

import com.bizkredit.credit.enums.AnalystRecommendation;
import com.bizkredit.credit.enums.ProposalStatus;
import com.bizkredit.credit.enums.RiskCategory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.Positive;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "credit_proposal")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CreditProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long proposalId;

    // Owned by sme-loan-service - id only, fetched over Feign when needed.
    // Not @NotNull: set by the service from the URL path param after @Valid
    // has already run on the incoming request body, which never includes it.
    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    private Long analystId;

    // The numeric score (0-100 or 0-maxScore) - auto-computed by scorecard engine
    // or set as output field. Renamed from 'scorecardRating' to avoid confusion with ratingLabel.
    private BigDecimal computedRatingScore;

    @Enumerated(EnumType.STRING)
    private RiskCategory riskCategory;

    // Which scorecard produced computedRatingScore/riskCategory, for audit/traceability.
    private Long scorecardId;

    // The raw computed score before mapping to a rating band (0..scorecard.maxScore).
    private Integer computedScore;

    // The letter grade (e.g. "AAA", "BBB", "B") - analyst POSTs this, system auto-fills
    // if scorecard engine runs, or analyst can manually set it.
    private String ratingLabel;

    // True if scorecardRating/riskCategory were last set by the auto-scoring engine;
    // false once an analyst manually edits them via PUT. Prevents auto-compute from
    // silently overwriting a deliberate analyst override on submit.
    @Builder.Default
    private Boolean scorecardAutoComputed = true;

    @Positive(message = "Suggested amount must be positive")
    private BigDecimal suggestedAmount;

    @Positive(message = "Suggested rate must be positive")
    private BigDecimal suggestedRate;

    private Integer tenure;

    @Column(length = 1000)
    private String conditions;

    @Enumerated(EnumType.STRING)
    private AnalystRecommendation analystRecommendation;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ProposalStatus status = ProposalStatus.DRAFT;
}
