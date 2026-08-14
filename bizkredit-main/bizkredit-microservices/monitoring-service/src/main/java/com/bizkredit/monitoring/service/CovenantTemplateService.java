package com.bizkredit.monitoring.service;

import com.bizkredit.monitoring.client.CollateralGateway;
import com.bizkredit.monitoring.entity.Covenant;
import com.bizkredit.monitoring.entity.CovenantTemplate;
import com.bizkredit.monitoring.enums.CovenantStatus;
import com.bizkredit.monitoring.enums.CovenantTemplateStatus;
import com.bizkredit.monitoring.enums.CovenantType;
import com.bizkredit.monitoring.enums.ProductType;
import com.bizkredit.monitoring.exception.BadRequestException;
import com.bizkredit.monitoring.exception.ResourceNotFoundException;
import com.bizkredit.monitoring.repository.CovenantRepository;
import com.bizkredit.monitoring.repository.CovenantTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CovenantTemplateService {

    private final CovenantTemplateRepository templateRepository;
    private final CovenantRepository covenantRepository;
    private final CollateralGateway collateralGateway;

    @Transactional
    public CovenantTemplate createTemplate(CovenantTemplate template, Long createdById) {
        if (templateRepository.existsByTemplateName(template.getTemplateName())) {
            throw new BadRequestException("A template named '" + template.getTemplateName() + "' already exists");
        }
        template.setCreatedById(createdById);
        template.setStatus(CovenantTemplateStatus.ACTIVE);
        CovenantTemplate saved = templateRepository.save(template);
        log.info("Covenant template '{}' created (id={})", saved.getTemplateName(), saved.getTemplateId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CovenantTemplate> getTemplates(CovenantType covenantType, ProductType productType) {
        List<CovenantTemplate> templates = covenantType != null
                ? templateRepository.findByCovenantType(covenantType)
                : templateRepository.findAll();

        // Filtering by product type happens in memory rather than in the
        // query - applicableProductTypes is a @ElementCollection, and this
        // catalogue is small enough that an extra join/IN-clause isn't
        // worth the added query complexity.
        if (productType != null) {
            templates = templates.stream()
                    .filter(t -> t.getApplicableProductTypes() == null
                            || t.getApplicableProductTypes().isEmpty()
                            || t.getApplicableProductTypes().contains(productType))
                    .toList();
        }
        return templates;
    }

    @Transactional(readOnly = true)
    public CovenantTemplate getTemplateById(Long templateId) {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Covenant template not found: " + templateId));
    }

    @Transactional
    public CovenantTemplate updateTemplate(Long templateId, CovenantTemplate updates) {
        CovenantTemplate existing = getTemplateById(templateId);

        if (updates.getTemplateName() != null && !updates.getTemplateName().equals(existing.getTemplateName())) {
            if (templateRepository.existsByTemplateName(updates.getTemplateName())) {
                throw new BadRequestException("A template named '" + updates.getTemplateName() + "' already exists");
            }
            existing.setTemplateName(updates.getTemplateName());
        }
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getDefaultThresholdValue() != null) existing.setDefaultThresholdValue(updates.getDefaultThresholdValue());
        if (updates.getDefaultMonitoringFrequency() != null) existing.setDefaultMonitoringFrequency(updates.getDefaultMonitoringFrequency());
        if (updates.getApplicableProductTypes() != null) existing.setApplicableProductTypes(updates.getApplicableProductTypes());
        if (updates.getStatus() != null) existing.setStatus(updates.getStatus());

        return templateRepository.save(existing);
    }

    /**
     * Applies a template to a facility - creates a real Covenant (Module
     * 4.7) with CovenantType/Description/ThresholdValue/MonitoringFrequency
     * pre-filled from the template. The applied covenant is a fully
     * independent record from that point on: overriding it afterwards (AC
     * #4) or deprecating the template later (AC #5) never touches it, since
     * there's no ongoing link stored back to the template.
     */
    @Transactional
    public Covenant applyTemplate(Long templateId, Long facilityId) {
        CovenantTemplate template = getTemplateById(templateId);
        collateralGateway.getFacility(facilityId); // validates the facility exists

        Covenant covenant = Covenant.builder()
                .facilityId(facilityId)
                .covenantType(template.getCovenantType())
                .description(template.getDescription())
                .thresholdValue(template.getDefaultThresholdValue())
                .monitoringFrequency(template.getDefaultMonitoringFrequency())
                .status(CovenantStatus.ACTIVE)
                .build();

        Covenant saved = covenantRepository.save(covenant);
        log.info("Applied covenant template '{}' to facility {} (new covenant id={})",
                template.getTemplateName(), facilityId, saved.getCovenantId());
        return saved;
    }
}
