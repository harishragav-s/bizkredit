package com.bizkredit.sme.service;

import com.bizkredit.sme.entity.LoanProduct;
import com.bizkredit.sme.enums.LoanProductStatus;
import com.bizkredit.sme.exception.BadRequestException;
import com.bizkredit.sme.exception.ResourceNotFoundException;
import com.bizkredit.sme.repository.LoanProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanProductService {

    private final LoanProductRepository loanProductRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public LoanProduct createProduct(LoanProduct product, Long createdById) {
        if (loanProductRepository.existsByProductCode(product.getProductCode())) {
            throw new BadRequestException("Product code already exists: " + product.getProductCode());
        }
        validateAmountAndTenure(product);
        product.setCreatedById(createdById);
        product.setStatus(LoanProductStatus.ACTIVE);
        LoanProduct saved = loanProductRepository.save(product);
        auditLogService.log(createdById, "CREATE", "LoanProduct", String.valueOf(saved.getProductId()));
        log.info("Loan product created: {}", saved.getProductCode());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<LoanProduct> getProducts(LoanProductStatus status) {
        if (status != null) return loanProductRepository.findByStatus(status);
        // Default: return only active products
        return loanProductRepository.findByStatus(LoanProductStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public LoanProduct getById(Long productId) {
        return loanProductRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan product not found: " + productId));
    }

    @Transactional
    public LoanProduct updateProduct(Long productId, LoanProduct updates) {
        LoanProduct existing = getById(productId);
        if (updates.getProductName() != null) existing.setProductName(updates.getProductName());
        if (updates.getMinAmount() != null) existing.setMinAmount(updates.getMinAmount());
        if (updates.getMaxAmount() != null) existing.setMaxAmount(updates.getMaxAmount());
        if (updates.getMinTenure() != null) existing.setMinTenure(updates.getMinTenure());
        if (updates.getMaxTenure() != null) existing.setMaxTenure(updates.getMaxTenure());
        if (updates.getBaseInterestRate() != null) existing.setBaseInterestRate(updates.getBaseInterestRate());
        if (updates.getRequiredDocuments() != null) existing.setRequiredDocuments(updates.getRequiredDocuments());
        if (updates.getEligibilityCriteria() != null) existing.setEligibilityCriteria(updates.getEligibilityCriteria());
        validateAmountAndTenure(existing);
        auditLogService.log(null, "UPDATE", "LoanProduct", String.valueOf(productId));
        return loanProductRepository.save(existing);
    }

    @Transactional
    public LoanProduct updateStatus(Long productId, LoanProductStatus status) {
        LoanProduct product = getById(productId);
        product.setStatus(status);
        auditLogService.log(null, "STATUS_CHANGE", "LoanProduct", String.valueOf(productId));
        return loanProductRepository.save(product);
    }

    private void validateAmountAndTenure(LoanProduct p) {
        if (p.getMinAmount() != null && p.getMaxAmount() != null
                && p.getMinAmount().compareTo(p.getMaxAmount()) >= 0) {
            throw new BadRequestException("MinAmount must be less than MaxAmount");
        }
        if (p.getMinTenure() != null && p.getMaxTenure() != null
                && p.getMinTenure() >= p.getMaxTenure()) {
            throw new BadRequestException("MinTenure must be less than MaxTenure");
        }
    }
}
