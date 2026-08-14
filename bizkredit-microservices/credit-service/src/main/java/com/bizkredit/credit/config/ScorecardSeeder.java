package com.bizkredit.credit.config;

import com.bizkredit.credit.entity.ScorecardModel;
import com.bizkredit.credit.entity.ScorecardModel.RatingBand;
import com.bizkredit.credit.entity.ScorecardModel.ScorecardParameter;
import com.bizkredit.credit.enums.ProductType;
import com.bizkredit.credit.enums.RiskCategory;
import com.bizkredit.credit.enums.ScorecardStatus;
import com.bizkredit.credit.repository.ScorecardModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Seeds one ACTIVE scorecard per ProductType on startup, so that
// FinancialAnalysisService.applyAutoScorecard() has something to run
// against the moment a credit analyst creates a proposal - without
// this, auto-scoring silently no-ops (by design, so proposal creation
// is never blocked on scorecard setup) and the analyst would be left
// to fill in rating/risk category by hand, which defeats the point of
// having an automated scorecard engine at all.
//
// Parameters use the fields ScoringFieldResolver actually supports:
// FinancialStatement (dscr, currentRatio, debtEquityRatio),
// SMEBusiness (yearsInOperation, annualTurnover), Promoter (creditScore).
// Weights sum to 100 per scorecard. Rules use the simple format
// ">=X:points,...,*:points" that ScorecardEngine.scoreSimpleFormat parses.
//
// Only runs if the scorecard_model table is empty, so it's safe to
// restart the service repeatedly without creating duplicates.
@Slf4j
@Component
@RequiredArgsConstructor
public class ScorecardSeeder implements CommandLineRunner {

    private final ScorecardModelRepository scorecardModelRepository;

    @Override
    public void run(String... args) {
        if (scorecardModelRepository.count() > 0) {
            log.info("Scorecards already exist ({}) - skipping seed.", scorecardModelRepository.count());
            return;
        }

        log.info("No scorecards found - seeding one ACTIVE scorecard per product type.");

        for (ProductType type : ProductType.values()) {
            scorecardModelRepository.save(buildStandardScorecard(type));
        }

        log.info("Seeded {} scorecards.", ProductType.values().length);
    }

    private ScorecardModel buildStandardScorecard(ProductType type) {
        return ScorecardModel.builder()
                .scorecardName("Standard Scorecard - " + type.name().replace('_', ' '))
                .productType(type)
                .maxScore(100)
                .status(ScorecardStatus.ACTIVE)
                .effectiveDate(LocalDate.now())
                .parameters(List.of(
                        // DSCR - debt service coverage; higher is safer
                        new ScorecardParameter(
                                "Debt Service Coverage Ratio",
                                "FinancialStatement", "dscr",
                                new BigDecimal("35"),
                                ">=2.0:100,>=1.5:80,>=1.2:60,>=1.0:40,*:15"
                        ),
                        // Current ratio - short-term liquidity
                        new ScorecardParameter(
                                "Current Ratio",
                                "FinancialStatement", "currentRatio",
                                new BigDecimal("20"),
                                ">=2.0:100,>=1.5:80,>=1.0:55,*:20"
                        ),
                        // Debt/Equity - leverage; lower is safer
                        new ScorecardParameter(
                                "Debt-to-Equity Ratio",
                                "FinancialStatement", "debtEquityRatio",
                                new BigDecimal("20"),
                                "<=0.5:100,<=1.0:75,<=2.0:50,*:20"
                        ),
                        // Years in operation - business maturity/track record
                        new ScorecardParameter(
                                "Years in Operation",
                                "SMEBusiness", "yearsInOperation",
                                new BigDecimal("15"),
                                ">=10:100,>=5:75,>=2:50,*:20"
                        ),
                        // Promoter credit score - personal creditworthiness
                        new ScorecardParameter(
                                "Promoter Credit Score",
                                "Promoter", "creditScore",
                                new BigDecimal("10"),
                                ">=750:100,>=700:80,>=650:55,*:20"
                        )
                ))
                .ratingBands(List.of(
                        new RatingBand(85, 100, "AAA", RiskCategory.LOW),
                        new RatingBand(70, 84, "AA", RiskCategory.LOW),
                        new RatingBand(55, 69, "A", RiskCategory.MEDIUM),
                        new RatingBand(40, 54, "BBB", RiskCategory.MEDIUM),
                        new RatingBand(25, 39, "BB", RiskCategory.HIGH),
                        new RatingBand(0, 24, "B", RiskCategory.WATCHLIST)
                ))
                .build();
    }
}
