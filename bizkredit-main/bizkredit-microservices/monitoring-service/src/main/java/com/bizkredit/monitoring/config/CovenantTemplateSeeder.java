package com.bizkredit.monitoring.config;

import com.bizkredit.monitoring.entity.CovenantTemplate;
import com.bizkredit.monitoring.enums.CovenantTemplateStatus;
import com.bizkredit.monitoring.enums.CovenantType;
import com.bizkredit.monitoring.enums.MonitoringFrequency;
import com.bizkredit.monitoring.enums.ProductType;
import com.bizkredit.monitoring.repository.CovenantTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds a standard set of covenant templates on first startup, so
 * Relationship Managers have usable templates immediately rather than
 * facing an empty library and having to invent standard banking covenants
 * from scratch. Admin can still add their own on top of these.
 *
 * Only runs when the table is completely empty - it will never overwrite
 * or duplicate templates an Admin has since created or edited.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CovenantTemplateSeeder implements CommandLineRunner {

    private final CovenantTemplateRepository repository;

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            log.info("Covenant templates already exist - skipping seed.");
            return;
        }

        List<CovenantTemplate> defaults = List.of(
                // --- Financial covenants (numeric thresholds, auto-checkable) ---
                template("Minimum Current Ratio", CovenantType.FINANCIAL,
                        "Maintain Current Ratio of at least 1.25x",
                        new BigDecimal("1.25"), MonitoringFrequency.QUARTERLY,
                        List.of(ProductType.TERM_LOAN, ProductType.WORKING_CAPITAL_CC, ProductType.OVERDRAFT_FACILITY)),

                template("Maximum Debt-Equity Ratio", CovenantType.FINANCIAL,
                        "Debt-to-Equity Ratio not to exceed 2.0x",
                        new BigDecimal("2.00"), MonitoringFrequency.QUARTERLY,
                        List.of(ProductType.TERM_LOAN, ProductType.EQUIPMENT_LOAN)),

                template("Minimum DSCR", CovenantType.FINANCIAL,
                        "Debt Service Coverage Ratio of at least 1.50x",
                        new BigDecimal("1.50"), MonitoringFrequency.QUARTERLY,
                        List.of(ProductType.TERM_LOAN, ProductType.EQUIPMENT_LOAN)),

                template("Minimum Net Worth", CovenantType.FINANCIAL,
                        "Tangible Net Worth to remain above the sanction-date level",
                        null, MonitoringFrequency.ANNUAL,
                        List.of(ProductType.TERM_LOAN, ProductType.WORKING_CAPITAL_CC)),

                template("Minimum EBITDA Margin", CovenantType.FINANCIAL,
                        "EBITDA margin of at least 10%",
                        new BigDecimal("10.00"), MonitoringFrequency.QUARTERLY,
                        List.of(ProductType.TERM_LOAN, ProductType.WORKING_CAPITAL_CC)),

                // --- Non-financial covenants (obligations, evidence-based) ---
                template("Quarterly Financial Statements", CovenantType.NON_FINANCIAL,
                        "Submit unaudited quarterly financial statements within 45 days of quarter end",
                        null, MonitoringFrequency.QUARTERLY, List.of()),

                template("Annual Audited Accounts", CovenantType.NON_FINANCIAL,
                        "Submit audited annual accounts within 180 days of financial year end",
                        null, MonitoringFrequency.ANNUAL, List.of()),

                template("Insurance Coverage", CovenantType.NON_FINANCIAL,
                        "Maintain comprehensive insurance on all charged assets, bank as loss payee",
                        null, MonitoringFrequency.ANNUAL, List.of()),

                template("No Additional Borrowing", CovenantType.NON_FINANCIAL,
                        "No additional borrowing from other lenders without prior written consent",
                        null, MonitoringFrequency.QUARTERLY, List.of()),

                template("Stock & Receivables Statement", CovenantType.NON_FINANCIAL,
                        "Submit monthly stock and book-debt statement within 15 days of month end",
                        null, MonitoringFrequency.MONTHLY,
                        List.of(ProductType.WORKING_CAPITAL_CC, ProductType.OVERDRAFT_FACILITY, ProductType.INVOICE_FINANCING)),

                template("No Change in Management", CovenantType.NON_FINANCIAL,
                        "No change in controlling shareholding or key management without prior consent",
                        null, MonitoringFrequency.ANNUAL, List.of())
        );

        repository.saveAll(defaults);
        log.info("Seeded {} default covenant templates.", defaults.size());
    }

    private CovenantTemplate template(String name, CovenantType type, String description,
                                       BigDecimal threshold, MonitoringFrequency frequency,
                                       List<ProductType> productTypes) {
        return CovenantTemplate.builder()
                .templateName(name)
                .covenantType(type)
                .description(description)
                .defaultThresholdValue(threshold)
                .defaultMonitoringFrequency(frequency)
                .applicableProductTypes(new java.util.ArrayList<>(productTypes))
                .status(CovenantTemplateStatus.ACTIVE)
                .build();
    }
}
