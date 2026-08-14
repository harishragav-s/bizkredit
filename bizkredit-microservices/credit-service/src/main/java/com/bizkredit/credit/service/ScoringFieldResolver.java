package com.bizkredit.credit.service;

import com.bizkredit.credit.dto.PromoterDTO;
import com.bizkredit.credit.dto.SMEBusinessDTO;
import com.bizkredit.credit.entity.FinancialStatement;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Resolves a ScorecardModel.ScorecardParameter (fieldSource + fieldName) to an actual
 * value pulled from the application's latest FinancialStatement, its SMEBusiness, or the
 * business's Promoter(s).
 *
 * Deliberately explicit (no reflection-by-string) so that:
 *  - unknown / mistyped field names fail loudly at scorecard-create time, not silently at
 *    compute time
 *  - every supported field is reviewable in one place
 *
 * To support a new field for scoring, add one line to the relevant map below.
 */
public final class ScoringFieldResolver {

    private ScoringFieldResolver() {
    }

    /** fieldName -> extractor, for fieldSource = "FinancialStatement" */
    private static final Map<String, Function<FinancialStatement, Object>> FINANCIAL_STATEMENT_FIELDS = Map.of(
            "revenue", FinancialStatement::getRevenue,
            "ebitda", FinancialStatement::getEbitda,
            "pat", FinancialStatement::getPat,
            "totalAssets", FinancialStatement::getTotalAssets,
            "totalLiabilities", FinancialStatement::getTotalLiabilities,
            "netWorth", FinancialStatement::getNetWorth,
            "currentRatio", FinancialStatement::getCurrentRatio,
            "debtEquityRatio", FinancialStatement::getDebtEquityRatio,
            "dscr", FinancialStatement::getDscr
    );

    /** fieldName -> extractor, for fieldSource = "SMEBusiness" (sme-loan-service, via Feign) */
    private static final Map<String, Function<SMEBusinessDTO, Object>> SME_BUSINESS_FIELDS = Map.of(
            "yearsInOperation", SMEBusinessDTO::getYearsInOperation,
            "annualTurnover", SMEBusinessDTO::getAnnualTurnover,
            "employeeCount", SMEBusinessDTO::getEmployeeCount,
            "industry", SMEBusinessDTO::getIndustry,
            "entityType", SMEBusinessDTO::getEntityType,
            "kycStatus", SMEBusinessDTO::getKycStatus
    );

    /**
     * fieldName -> extractor, for fieldSource = "Promoter".
     * When a business has multiple promoters, the engine uses the one with the highest
     * shareholding to represent the business (falls back to first promoter if shareholding
     * is unset on all of them).
     */
    private static final Map<String, Function<PromoterDTO, Object>> PROMOTER_FIELDS = Map.of(
            "creditScore", PromoterDTO::getCreditScore,
            "personalNetWorth", PromoterDTO::getPersonalNetWorth,
            "shareholdingPercent", PromoterDTO::getShareholdingPercent
    );

    public static java.util.Set<String> supportedSources() {
        return java.util.Set.of("FinancialStatement", "SMEBusiness", "Promoter");
    }

    public static boolean isSupportedField(String fieldSource, String fieldName) {
        if (fieldSource == null || fieldName == null) return false;
        return switch (fieldSource) {
            case "FinancialStatement" -> FINANCIAL_STATEMENT_FIELDS.containsKey(fieldName);
            case "SMEBusiness" -> SME_BUSINESS_FIELDS.containsKey(fieldName);
            case "Promoter" -> PROMOTER_FIELDS.containsKey(fieldName);
            default -> false;
        };
    }

    /**
     * Resolve the raw value for a parameter from the data context. Returns null if the
     * field source is unsupported, the underlying record is missing, or the value itself
     * is null - callers should treat null as "cannot score this parameter".
     */
    public static Object resolve(String fieldSource, String fieldName, ScoringContext context) {
        if (fieldSource == null || fieldName == null) return null;
        return switch (fieldSource) {
            case "FinancialStatement" -> {
                FinancialStatement fs = context.latestFinancialStatement();
                var extractor = FINANCIAL_STATEMENT_FIELDS.get(fieldName);
                yield (fs == null || extractor == null) ? null : extractor.apply(fs);
            }
            case "SMEBusiness" -> {
                SMEBusinessDTO biz = context.business();
                Function<SMEBusinessDTO, Object> extractor = SME_BUSINESS_FIELDS.get(fieldName);
                yield (biz == null || extractor == null) ? null : extractor.apply(biz);
            }
            case "Promoter" -> {
                PromoterDTO promoter = primaryPromoter(context.promoters());
                Function<PromoterDTO, Object> extractor = PROMOTER_FIELDS.get(fieldName);
                yield (promoter == null || extractor == null) ? null : extractor.apply(promoter);
            }
            default -> null;
        };
    }

    private static PromoterDTO primaryPromoter(List<PromoterDTO> promoters) {
        if (promoters == null || promoters.isEmpty()) return null;
        return promoters.stream()
                .filter(p -> p.getShareholdingPercent() != null)
                .max((a, b) -> a.getShareholdingPercent().compareTo(b.getShareholdingPercent()))
                .orElse(promoters.get(0));
    }

    /**
     * Bundles the data needed to resolve any parameter for one application.
     * business/promoters come from sme-loan-service via SmeGateway (Feign) -
     * not from a local cross-schema JPA read.
     */
    public record ScoringContext(
            FinancialStatement latestFinancialStatement,
            SMEBusinessDTO business,
            List<PromoterDTO> promoters
    ) {
    }

    /** Helper to coerce a resolved Object value into BigDecimal for numeric band evaluation. */
    public static BigDecimal asNumeric(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Integer i) return BigDecimal.valueOf(i);
        if (value instanceof Long l) return BigDecimal.valueOf(l);
        if (value instanceof Double d) return BigDecimal.valueOf(d);
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String asText(Object value) {
        return value == null ? null : value.toString().toLowerCase(Locale.ROOT);
    }
}
