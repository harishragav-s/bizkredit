package com.bizkredit.credit.service;

import com.bizkredit.credit.client.SmeGateway;
import com.bizkredit.credit.dto.LoanApplicationDTO;
import com.bizkredit.credit.dto.PromoterDTO;
import com.bizkredit.credit.dto.SMEBusinessDTO;
import com.bizkredit.credit.entity.FinancialStatement;
import com.bizkredit.credit.entity.ScorecardModel;
import com.bizkredit.credit.enums.ProductType;
import com.bizkredit.credit.enums.ScorecardStatus;
import com.bizkredit.credit.exception.BadRequestException;
import com.bizkredit.credit.exception.ResourceNotFoundException;
import com.bizkredit.credit.repository.FinancialStatementRepository;
import com.bizkredit.credit.repository.ScorecardModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScorecardService {

    private final ScorecardModelRepository scorecardRepository;
    private final FinancialStatementRepository financialStatementRepository;
    private final SmeGateway smeGateway;
    private final ScorecardEngine scorecardEngine;
    private final AuditLogService auditLogService;

    @Transactional
    public ScorecardModel createScorecard(ScorecardModel scorecard, Long createdById) {
        validateScorecard(scorecard);

        // Only one active scorecard allowed per product type
        if (scorecard.getStatus() == ScorecardStatus.ACTIVE) {
            List<ScorecardModel> existing = scorecardRepository.findByProductTypeAndStatus(
                    scorecard.getProductType(), ScorecardStatus.ACTIVE);
            if (!existing.isEmpty()) {
                throw new BadRequestException(
                        "An active scorecard already exists for " + scorecard.getProductType());
            }
        }

        scorecard.setCreatedById(createdById);
        ScorecardModel saved = scorecardRepository.save(scorecard);
        auditLogService.log(createdById, "CREATE", "ScorecardModel", String.valueOf(saved.getScorecardId()));
        log.info("Scorecard created: {} for {}", saved.getScorecardName(), saved.getProductType());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<ScorecardModel> getScorecards(ProductType productType, ScorecardStatus status) {
        if (productType != null && status != null) {
            return scorecardRepository.findByProductTypeAndStatus(productType, status);
        }
        if (status != null) return scorecardRepository.findByStatus(status);
        return scorecardRepository.findAll();
    }

    @Transactional(readOnly = true)
    public ScorecardModel getById(Long scorecardId) {
        return scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard not found: " + scorecardId));
    }

    @Transactional
    public ScorecardModel updateScorecard(Long scorecardId, ScorecardModel updates) {
        ScorecardModel existing = getById(scorecardId);
        if (existing.getStatus() == ScorecardStatus.ACTIVE) {
            throw new BadRequestException("Cannot update an ACTIVE scorecard. Archive it first.");
        }
        if (updates.getScorecardName() != null) existing.setScorecardName(updates.getScorecardName());
        if (updates.getMaxScore() != null) existing.setMaxScore(updates.getMaxScore());
        if (updates.getParameters() != null) existing.setParameters(updates.getParameters());
        if (updates.getRatingBands() != null) existing.setRatingBands(updates.getRatingBands());
        if (updates.getEffectiveDate() != null) existing.setEffectiveDate(updates.getEffectiveDate());
        validateScorecard(existing);
        auditLogService.log(null, "UPDATE", "ScorecardModel", String.valueOf(scorecardId));
        return scorecardRepository.save(existing);
    }

    // POST /api/scorecards/{id}/compute - runs the real weighted scoring engine
    // against the application's latest financial statement, SME business, and promoter data.
    @Transactional(readOnly = true)
    public Map<String, Object> computeScore(Long scorecardId, Long applicationId) {
        ScorecardModel scorecard = getById(scorecardId);
        ScorecardEngine.ScoringResult scoring = computeForApplication(scorecard, applicationId);

        Map<String, Object> result = new HashMap<>();
        result.put("scorecardId", scorecardId);
        result.put("applicationId", applicationId);
        result.put("computedScore", scoring.computedScore());
        result.put("maxScore", scoring.maxScore());
        result.put("scorecardRating", scoring.rating());
        result.put("riskCategory", scoring.riskCategory());
        result.put("breakdown", scoring.breakdown());
        if (scoring.partialData()) {
            result.put("note", "Computed from partial data - some parameters had no source value "
                    + "(only " + scoring.totalWeightApplied() + " of 100 weight applied)");
        }
        return result;
    }

    /**
     * Resolves the application's business, latest financial statement, and promoters,
     * then runs the engine. Shared by the manual /compute endpoint and the automatic
     * compute-on-proposal flow in FinancialAnalysisService.
     *
     * application/business/promoters are fetched from sme-loan-service over Feign
     * (SmeGateway) - credit-service no longer reads sme-loan-service's tables directly
     * for this. Only the financial statement (owned by credit-service itself) still
     * comes from the local repository.
     */
    @Transactional(readOnly = true)
    public ScorecardEngine.ScoringResult computeForApplication(ScorecardModel scorecard, Long applicationId) {
        LoanApplicationDTO application = smeGateway.getApplication(applicationId);

        FinancialStatement latestStatement = financialStatementRepository
                .findByApplicationId(applicationId).stream()
                .max(Comparator.comparing(FinancialStatement::getFinancialYear)
                        .thenComparing(FinancialStatement::getStatementId))
                .orElse(null);

        // application.getBusiness() is always null (see LoanApplicationDTO) -
        // the businessId field is what's actually populated over the wire.
        SMEBusinessDTO business = smeGateway.getBusiness(application.getBusinessId());

        List<PromoterDTO> promoters = business != null
                ? smeGateway.getPromoters(business.getBusinessId())
                : List.of();

        ScoringFieldResolver.ScoringContext context = new ScoringFieldResolver.ScoringContext(
                latestStatement, business, promoters);

        return scorecardEngine.compute(scorecard, context);
    }

    /**
     * Finds the single ACTIVE scorecard for a product type, if any. Used to auto-score
     * credit proposals - returns null (rather than throwing) when no active scorecard
     * exists yet, so proposal creation isn't blocked on scorecard setup.
     */
    @Transactional(readOnly = true)
    public ScorecardModel findActiveScorecardFor(ProductType productType) {
        List<ScorecardModel> active = scorecardRepository.findByProductTypeAndStatus(productType, ScorecardStatus.ACTIVE);
        if (active.isEmpty()) return null;
        if (active.size() > 1) {
            log.warn("Multiple ACTIVE scorecards found for {} - using the first one (id={})",
                    productType, active.get(0).getScorecardId());
        }
        return active.get(0);
    }

    private void validateScorecard(ScorecardModel scorecard) {
        if (scorecard.getParameters() != null && !scorecard.getParameters().isEmpty()) {
            BigDecimal totalWeight = scorecard.getParameters().stream()
                    .map(p -> p.getWeight() != null ? p.getWeight() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalWeight.compareTo(new BigDecimal("100")) != 0) {
                throw new BadRequestException(
                        "Parameter weights must sum to 100. Current sum: " + totalWeight);
            }
            for (ScorecardModel.ScorecardParameter param : scorecard.getParameters()) {
                if (!ScoringFieldResolver.isSupportedField(param.getFieldSource(), param.getFieldName())) {
                    throw new BadRequestException(
                            "Unsupported fieldSource/fieldName for parameter '" + param.getParameterName()
                                    + "': " + param.getFieldSource() + "." + param.getFieldName()
                                    + ". Supported sources: " + ScoringFieldResolver.supportedSources());
                }
                if (param.getScoringRules() == null || param.getScoringRules().isBlank()) {
                    throw new BadRequestException(
                            "scoringRules is required for parameter '" + param.getParameterName() + "'");
                }
            }
        }
    }
}
