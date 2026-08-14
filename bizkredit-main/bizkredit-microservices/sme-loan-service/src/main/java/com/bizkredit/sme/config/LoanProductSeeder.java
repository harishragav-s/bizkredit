package com.bizkredit.sme.config;

import com.bizkredit.sme.entity.LoanProduct;
import com.bizkredit.sme.enums.LoanProductStatus;
import com.bizkredit.sme.enums.ProductType;
import com.bizkredit.sme.repository.LoanProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

// Seeds a standard set of 5 loan products (one per ProductType) on
// startup so an SME applicant always has real products to choose from
// without an admin having to create them manually first. Amounts are in
// crores (1,00,00,000+) - this platform targets larger SMEs, not
// micro-loans, so every product's minimum starts at Rs. 1 crore. Only
// runs if the loan_product table is empty, so it's safe to restart the
// service repeatedly without creating duplicates.
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanProductSeeder implements CommandLineRunner {

    private final LoanProductRepository loanProductRepository;

    @Override
    public void run(String... args) {
        if (loanProductRepository.count() > 0) {
            log.info("Loan products already exist ({}) - skipping seed.", loanProductRepository.count());
            return;
        }

        log.info("No loan products found - seeding standard product catalogue.");

        loanProductRepository.save(LoanProduct.builder()
                .productCode("TL-STD-01")
                .productName("Standard Term Loan")
                .productType(ProductType.TERM_LOAN)
                .minAmount(new BigDecimal("10000000"))
                .maxAmount(new BigDecimal("250000000"))
                .minTenure(12)
                .maxTenure(84)
                .baseInterestRate(new BigDecimal("11.50"))
                .status(LoanProductStatus.ACTIVE)
                .build());

        loanProductRepository.save(LoanProduct.builder()
                .productCode("WCC-STD-01")
                .productName("Working Capital Cash Credit")
                .productType(ProductType.WORKING_CAPITAL_CC)
                .minAmount(new BigDecimal("10000000"))
                .maxAmount(new BigDecimal("150000000"))
                .minTenure(12)
                .maxTenure(12)
                .baseInterestRate(new BigDecimal("10.75"))
                .status(LoanProductStatus.ACTIVE)
                .build());

        loanProductRepository.save(LoanProduct.builder()
                .productCode("OD-STD-01")
                .productName("Overdraft Facility")
                .productType(ProductType.OVERDRAFT_FACILITY)
                .minAmount(new BigDecimal("10000000"))
                .maxAmount(new BigDecimal("100000000"))
                .minTenure(12)
                .maxTenure(12)
                .baseInterestRate(new BigDecimal("12.25"))
                .status(LoanProductStatus.ACTIVE)
                .build());

        loanProductRepository.save(LoanProduct.builder()
                .productCode("IF-STD-01")
                .productName("Invoice Financing")
                .productType(ProductType.INVOICE_FINANCING)
                .minAmount(new BigDecimal("10000000"))
                .maxAmount(new BigDecimal("80000000"))
                .minTenure(1)
                .maxTenure(6)
                .baseInterestRate(new BigDecimal("13.00"))
                .status(LoanProductStatus.ACTIVE)
                .build());

        loanProductRepository.save(LoanProduct.builder()
                .productCode("EL-STD-01")
                .productName("Equipment Loan")
                .productType(ProductType.EQUIPMENT_LOAN)
                .minAmount(new BigDecimal("10000000"))
                .maxAmount(new BigDecimal("200000000"))
                .minTenure(12)
                .maxTenure(60)
                .baseInterestRate(new BigDecimal("11.00"))
                .status(LoanProductStatus.ACTIVE)
                .build());

        log.info("Seeded 5 standard loan products.");

    }
}
