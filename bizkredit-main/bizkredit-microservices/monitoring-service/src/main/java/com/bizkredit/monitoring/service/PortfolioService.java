package com.bizkredit.monitoring.service;

import com.bizkredit.monitoring.client.CollateralGateway;
import com.bizkredit.monitoring.client.SmeLoanServiceClient;
import com.bizkredit.monitoring.dto.FacilityDTO;
import com.bizkredit.monitoring.repository.NPARecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Portfolio analytics.
 *
 * Previously ran cross-schema JPQL aggregates directly against
 * bizkredit_collateral_db.facility_account (getPortfolioSummary,
 * getAssetQualityDistribution) and a JOIN spanning both
 * bizkredit_collateral_db and bizkredit_sme_db for sector exposure. Feign
 * can't express a join, so all three now fetch facilities over HTTP
 * (CollateralGateway) and aggregate in memory here instead of in SQL.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioService {

    private final CollateralGateway collateralGateway;
    private final SmeLoanServiceClient smeLoanServiceClient;
    private final NPARecordRepository npaRecordRepository;
    private final com.bizkredit.monitoring.repository.CovenantRepository covenantRepository;
    private final com.bizkredit.monitoring.repository.EarlyWarningSignalRepository ewsRepository;

    // BP2-49/58 - covenant compliance summary across the whole portfolio.
    // NOTE: there is no CovenantTracking (per-period actuals) entity in this
    // codebase yet, so "compliant/breached" here reflects each Covenant's
    // current Status (ACTIVE/WAIVED/BREACHED) rather than period-by-period
    // history - a coarser signal than the full BP2-23 spec describes, but
    // still a real, live compliance rate rather than a placeholder.
    @Transactional(readOnly = true)
    public Map<String, Object> getCovenantCompliance() {
        List<com.bizkredit.monitoring.entity.Covenant> all = covenantRepository.findAll();
        long active = all.stream().filter(c -> c.getStatus() == com.bizkredit.monitoring.enums.CovenantStatus.ACTIVE).count();
        long breached = all.stream().filter(c -> c.getStatus() == com.bizkredit.monitoring.enums.CovenantStatus.BREACHED).count();
        long waived = all.stream().filter(c -> c.getStatus() == com.bizkredit.monitoring.enums.CovenantStatus.WAIVED).count();
        long trackable = active + breached;

        double complianceRatePercent = trackable > 0
                ? BigDecimal.valueOf(active).divide(BigDecimal.valueOf(trackable), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 100.0;

        List<Map<String, Object>> breachedList = all.stream()
                .filter(c -> c.getStatus() == com.bizkredit.monitoring.enums.CovenantStatus.BREACHED)
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("covenantId", c.getCovenantId());
                    m.put("facilityId", c.getFacilityId());
                    m.put("description", c.getDescription());
                    m.put("thresholdValue", c.getThresholdValue());
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("compliantCount", active);
        result.put("breachedCount", breached);
        result.put("waivedCount", waived);
        result.put("complianceRatePercent", complianceRatePercent);
        result.put("breachedCovenants", breachedList);
        return result;
    }

    // BP2-49/58 - EWS signal aggregation across the whole portfolio.
    @Transactional(readOnly = true)
    public Map<String, Object> getEwsSignals() {
        List<com.bizkredit.monitoring.entity.EarlyWarningSignal> open =
                ewsRepository.findByStatus(com.bizkredit.monitoring.enums.EWSStatus.OPEN);

        Map<String, Long> bySeverity = open.stream().collect(Collectors.groupingBy(
                e -> e.getSeverity().name(), Collectors.counting()));
        Map<String, Long> byType = open.stream().collect(Collectors.groupingBy(
                e -> e.getSignalType().name(), Collectors.counting()));

        Map<String, Object> result = new HashMap<>();
        result.put("openCount", open.size());
        result.put("bySeverity", bySeverity);
        result.put("bySignalType", byType);
        return result;
    }

    // BP2-49/58 - renewal pipeline widget (facilities expiring in 30/60/90 days).
    @Transactional(readOnly = true)
    public Map<String, Object> getRenewalPipeline() {
        List<FacilityDTO> within90 = collateralGateway.getExpiringFacilities(90);

        Map<String, Object> result = new HashMap<>();
        result.put("within30Days", within90.stream().filter(f -> withinDays(f, 30)).toList());
        result.put("within60Days", within90.stream().filter(f -> withinDays(f, 60)).toList());
        result.put("within90Days", within90);
        return result;
    }

    private boolean withinDays(FacilityDTO f, int days) {
        if (f.getMaturityDate() == null) return false;
        return !f.getMaturityDate().isAfter(java.time.LocalDate.now().plusDays(days));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPortfolioSummary() {
        List<FacilityDTO> active = collateralGateway.getFacilitiesByStatus("ACTIVE");
        Map<String, Object> result = new HashMap<>();

        BigDecimal sanctioned = active.stream()
                .map(f -> nz(f.getSanctionedLimit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = active.stream()
                .map(f -> nz(f.getOutstandingBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = active.size();

        double utilisationPercent = sanctioned.compareTo(BigDecimal.ZERO) > 0
                ? outstanding.divide(sanctioned, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue()
                : 0;

        result.put("totalSanctionedExposure", sanctioned);
        result.put("totalOutstanding", outstanding);
        result.put("activeFacilitiesCount", count);
        result.put("portfolioUtilisationPercent", utilisationPercent);

        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAssetQuality() {
        List<FacilityDTO> all = collateralGateway.getAllFacilities();
        Map<String, Object> result = new HashMap<>();

        Map<String, List<FacilityDTO>> byStatus = all.stream()
                .filter(f -> f.getStatus() != null)
                .collect(Collectors.groupingBy(FacilityDTO::getStatus));

        for (var entry : byStatus.entrySet()) {
            BigDecimal outstanding = entry.getValue().stream()
                    .map(f -> nz(f.getOutstandingBalance()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            result.put(entry.getKey(), Map.of(
                    "count", entry.getValue().size(),
                    "outstanding", outstanding
            ));
        }

        result.put("totalNPARecords", npaRecordRepository.findAll().size());

        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getSectorExposure() {
        List<FacilityDTO> active = collateralGateway.getFacilitiesByStatus("ACTIVE");

        // Industry isn't on the facility response - look it up per distinct
        // business. Bounded by the number of distinct businesses with an
        // active facility (typically small for a project like this), not
        // per facility, so this isn't the N+1 storm the FacilityDTO
        // comment warns against - just one extra hop industry doesn't
        // travel with today, since collateral-service's own FacilityAccount
        // no longer holds a business relation to flatten it from either.
        Map<Long, String> industryByBusiness = new HashMap<>();
        for (Long businessId : active.stream().map(FacilityDTO::getBusinessId).distinct().toList()) {
            if (businessId == null) continue;
            try {
                var body = smeLoanServiceClient.getBusiness(businessId);
                if (body != null && body.getData() != null) {
                    industryByBusiness.put(businessId, body.getData().getIndustry());
                }
            } catch (Exception e) {
                log.warn("Could not fetch industry for business {}: {}", businessId, e.getMessage());
            }
        }

        Map<String, BigDecimal> byIndustry = new HashMap<>();
        for (FacilityDTO f : active) {
            String industry = industryByBusiness.getOrDefault(f.getBusinessId(), "Unknown");
            byIndustry.merge(industry, nz(f.getOutstandingBalance()), BigDecimal::add);
        }

        return byIndustry.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .map(e -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("industry", e.getKey());
                    data.put("totalOutstanding", e.getValue());
                    return data;
                })
                .collect(Collectors.toList());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
