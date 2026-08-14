package com.bizkredit.credit;

import com.bizkredit.credit.dto.SMEBusinessDTO;
import com.bizkredit.credit.dto.PromoterDTO;
import com.bizkredit.credit.entity.FinancialStatement;
import com.bizkredit.credit.entity.ScorecardModel;
import com.bizkredit.credit.service.ScorecardEngine;
import com.bizkredit.credit.service.ScoringFieldResolver;
import com.bizkredit.credit.enums.ProductType;
import com.bizkredit.credit.enums.RiskCategory;
import com.bizkredit.credit.enums.ScorecardStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScorecardEngineTest {

    private final ScorecardEngine engine = new ScorecardEngine();

    private ScorecardModel.ScorecardParameter numericParam(String name, String source, String field,
                                                            int weight, String bandsJson) {
        return new ScorecardModel.ScorecardParameter(name, source, field, new BigDecimal(weight), bandsJson);
    }

    private ScorecardModel.RatingBand band(int min, int max, String rating, RiskCategory risk) {
        return new ScorecardModel.RatingBand(min, max, rating, risk);
    }

    @Test
    void numericBand_picksCorrectBandAndWeightsContribution() {
        String rules = "{\"bands\":["
                + "{\"min\":null,\"max\":1.0,\"points\":100},"
                + "{\"min\":1.0,\"max\":2.0,\"points\":60},"
                + "{\"min\":2.0,\"max\":null,\"points\":20}]}";

        ScorecardModel scorecard = ScorecardModel.builder()
                .scorecardId(1L)
                .productType(ProductType.TERM_LOAN)
                .maxScore(100)
                .status(ScorecardStatus.ACTIVE)
                .parameters(List.of(numericParam("Debt/Equity", "FinancialStatement", "debtEquityRatio", 100, rules)))
                .ratingBands(List.of(
                        band(0, 50, "B", RiskCategory.HIGH),
                        band(51, 80, "BBB", RiskCategory.MEDIUM),
                        band(81, 100, "AAA", RiskCategory.LOW)))
                .build();

        FinancialStatement statement = FinancialStatement.builder()
                .debtEquityRatio(new BigDecimal("0.8"))
                .build();

        ScoringFieldResolver.ScoringContext context = new ScoringFieldResolver.ScoringContext(
                statement, null, List.of());

        ScorecardEngine.ScoringResult result = engine.compute(scorecard, context);

        assertThat(result.computedScore()).isEqualTo(100);
        assertThat(result.rating()).isEqualTo("AAA");
        assertThat(result.riskCategory()).isEqualTo(RiskCategory.LOW);
        assertThat(result.partialData()).isFalse();
    }

    @Test
    void categoricalRule_matchesByValueCaseInsensitive() {
        String rules = "{\"values\":{\"Manufacturing\":90,\"Trading\":60},\"default\":40}";

        ScorecardModel scorecard = ScorecardModel.builder()
                .maxScore(100)
                .parameters(List.of(numericParam("Industry", "SMEBusiness", "industry", 100, rules)))
                .ratingBands(List.of(band(0, 100, "X", RiskCategory.MEDIUM)))
                .build();

        SMEBusinessDTO business = SMEBusinessDTO.builder().industry("manufacturing").build();
        ScoringFieldResolver.ScoringContext context = new ScoringFieldResolver.ScoringContext(null, business, List.of());

        ScorecardEngine.ScoringResult result = engine.compute(scorecard, context);

        assertThat(result.computedScore()).isEqualTo(90);
    }

    @Test
    void categoricalRule_fallsBackToDefaultWhenValueNotListed() {
        String rules = "{\"values\":{\"Manufacturing\":90},\"default\":40}";

        ScorecardModel scorecard = ScorecardModel.builder()
                .maxScore(100)
                .parameters(List.of(numericParam("Industry", "SMEBusiness", "industry", 100, rules)))
                .ratingBands(List.of(band(0, 100, "X", RiskCategory.MEDIUM)))
                .build();

        SMEBusinessDTO business = SMEBusinessDTO.builder().industry("Retail").build();
        ScoringFieldResolver.ScoringContext context = new ScoringFieldResolver.ScoringContext(null, business, List.of());

        ScorecardEngine.ScoringResult result = engine.compute(scorecard, context);

        assertThat(result.computedScore()).isEqualTo(40);
    }

    @Test
    void missingData_parameterContributesZeroAndFlagsPartialData() {
        String rules = "{\"bands\":[{\"min\":0,\"max\":null,\"points\":100}]}";

        ScorecardModel scorecard = ScorecardModel.builder()
                .maxScore(100)
                .parameters(List.of(
                        numericParam("Revenue", "FinancialStatement", "revenue", 50, rules),
                        numericParam("DSCR", "FinancialStatement", "dscr", 50, rules)))
                .ratingBands(List.of(band(0, 100, "X", RiskCategory.MEDIUM)))
                .build();

        ScoringFieldResolver.ScoringContext context = new ScoringFieldResolver.ScoringContext(null, null, List.of());

        ScorecardEngine.ScoringResult result = engine.compute(scorecard, context);

        assertThat(result.computedScore()).isEqualTo(0);
        assertThat(result.partialData()).isTrue();
        assertThat(result.breakdown()).hasSize(2);
        assertThat(result.breakdown().get(0).scored()).isFalse();
    }

    @Test
    void promoterField_usesHighestShareholdingPromoter() {
        String rules = "{\"bands\":[{\"min\":700,\"max\":null,\"points\":100},{\"min\":0,\"max\":700,\"points\":30}]}";

        ScorecardModel scorecard = ScorecardModel.builder()
                .maxScore(100)
                .parameters(List.of(numericParam("Promoter credit score", "Promoter", "creditScore", 100, rules)))
                .ratingBands(List.of(band(0, 100, "X", RiskCategory.MEDIUM)))
                .build();

        PromoterDTO minor = PromoterDTO.builder().creditScore(600).shareholdingPercent(new BigDecimal("10")).build();
        PromoterDTO majority = PromoterDTO.builder().creditScore(780).shareholdingPercent(new BigDecimal("90")).build();

        ScoringFieldResolver.ScoringContext context = new ScoringFieldResolver.ScoringContext(
                null, null, List.of(minor, majority));

        ScorecardEngine.ScoringResult result = engine.compute(scorecard, context);

        assertThat(result.computedScore()).isEqualTo(100);
    }

    @Test
    void multipleParameters_weightedAndScaledToMaxScore() {
        String highIsGood = "{\"bands\":[{\"min\":0,\"max\":1,\"points\":20},{\"min\":1,\"max\":null,\"points\":100}]}";
        String entityRules = "{\"values\":{\"PRIVATE_LIMITED\":100,\"PROPRIETORSHIP\":50}}";

        ScorecardModel scorecard = ScorecardModel.builder()
                .maxScore(50)
                .parameters(List.of(
                        numericParam("Current ratio", "FinancialStatement", "currentRatio", 70, highIsGood),
                        numericParam("Entity type", "SMEBusiness", "entityType", 30, entityRules)))
                .ratingBands(List.of(
                        band(0, 30, "B", RiskCategory.HIGH),
                        band(31, 50, "AA", RiskCategory.LOW)))
                .build();

        FinancialStatement statement = FinancialStatement.builder()
                .currentRatio(new BigDecimal("1.5"))
                .build();
        SMEBusinessDTO business = SMEBusinessDTO.builder().entityType("PRIVATE_LIMITED").build();

        ScoringFieldResolver.ScoringContext context = new ScoringFieldResolver.ScoringContext(
                statement, business, List.of());

        ScorecardEngine.ScoringResult result = engine.compute(scorecard, context);

        assertThat(result.computedScore()).isEqualTo(50);
        assertThat(result.rating()).isEqualTo("AA");
        assertThat(result.riskCategory()).isEqualTo(RiskCategory.LOW);
    }
}
