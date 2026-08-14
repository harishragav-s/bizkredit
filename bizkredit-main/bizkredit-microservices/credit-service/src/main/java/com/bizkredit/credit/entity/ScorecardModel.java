package com.bizkredit.credit.entity;

import com.bizkredit.credit.enums.ProductType;
import com.bizkredit.credit.enums.RiskCategory;
import com.bizkredit.credit.enums.ScorecardStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "scorecard_model")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ScorecardModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long scorecardId;

    private String scorecardName;

    @Enumerated(EnumType.STRING)
    private ProductType productType;

    private Integer maxScore;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ScorecardStatus status = ScorecardStatus.DRAFT;

    private Long createdById;

    private LocalDate effectiveDate;

    // FetchMode.SUBSELECT (not the default JOIN) is required here:
    // Hibernate cannot eagerly join-fetch two List ("bag") collections
    // in a single query - doing so throws MultipleBagFetchException,
    // since a join across two one-to-many collections produces a
    // Cartesian product Hibernate refuses to build. SUBSELECT instead
    // loads this collection with its own follow-up query (reusing the
    // same WHERE clause as the parent query), which sidesteps the bag
    // conflict while still eagerly loading before the Hibernate
    // session closes (needed since open-in-view=false).
    // FetchType.EAGER is required here, not just @Fetch(SUBSELECT) -
    // SUBSELECT only changes *how* Hibernate executes an eventual
    // fetch (one subselect query instead of N individual ones); it
    // does not by itself make a LAZY collection load eagerly. Without
    // EAGER here, this collection would still throw
    // LazyInitializationException when Jackson serializes it after
    // the session closes - the exact bug an @EntityGraph on
    // ScorecardModelRepository was originally added to prevent,
    // before that approach hit MultipleBagFetchException (both
    // parameters and ratingBands can't be @EntityGraph'd together).
    // That @EntityGraph has since been removed - this pair of
    // annotations on the entity itself replaces it.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "scorecard_parameter", joinColumns = @JoinColumn(name = "scorecard_id"))
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    @Builder.Default
    private List<ScorecardParameter> parameters = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "scorecard_rating_band", joinColumns = @JoinColumn(name = "scorecard_id"))
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    @Builder.Default
    private List<RatingBand> ratingBands = new ArrayList<>();

    // ── Embeddable sub-entities

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScorecardParameter {
        private String parameterName;
        // FinancialStatement / SMEBusiness / Promoter
        private String fieldSource;
        private String fieldName;
        private BigDecimal weight;
        // JSON string for configurable scoring rules
        @Column(columnDefinition = "TEXT")
        private String scoringRules;
    }

    @Embeddable
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RatingBand {
        private Integer minScore;
        private Integer maxScore;
        private String rating;  // AAA, AA, A, BBB, BB, B etc.
        @Enumerated(EnumType.STRING)
        private RiskCategory riskCategory;
    }
}
