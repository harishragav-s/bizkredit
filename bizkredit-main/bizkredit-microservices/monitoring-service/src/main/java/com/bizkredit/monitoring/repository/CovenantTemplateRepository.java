package com.bizkredit.monitoring.repository;

import com.bizkredit.monitoring.entity.CovenantTemplate;
import com.bizkredit.monitoring.enums.CovenantTemplateStatus;
import com.bizkredit.monitoring.enums.CovenantType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CovenantTemplateRepository extends JpaRepository<CovenantTemplate, Long> {

    boolean existsByTemplateName(String templateName);

    Optional<CovenantTemplate> findByTemplateName(String templateName);

    List<CovenantTemplate> findByCovenantType(CovenantType covenantType);

    List<CovenantTemplate> findByStatus(CovenantTemplateStatus status);

    List<CovenantTemplate> findByCovenantTypeAndStatus(CovenantType covenantType, CovenantTemplateStatus status);
}
