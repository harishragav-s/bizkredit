package com.bizkredit.monitoring.entity;

import com.bizkredit.monitoring.enums.CovenantTemplateStatus;
import com.bizkredit.monitoring.enums.CovenantType;
import com.bizkredit.monitoring.enums.MonitoringFrequency;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// BP2-43/52 - reusable covenant templates an Admin manages so RMs can
// one-click apply standard covenants (with sensible defaults) to a facility
// instead of typing the same description/threshold/frequency every time.
@Entity
@Table(name = "covenant_template")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CovenantTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long templateId;

    @NotBlank(message = "Template name is required")
    @Column(unique = true, nullable = false)
    private String templateName;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Covenant type is required")
    private CovenantType covenantType;

    @NotBlank(message = "Description is required")
    private String description;

    private BigDecimal defaultThresholdValue;

    @Enumerated(EnumType.STRING)
    private MonitoringFrequency defaultMonitoringFrequency;

    // Stored as a simple comma-joined string of ProductType names rather
    // than a join table - this project has no other list(enum) column and
    // doesn't need query-by-product-type at the SQL level, only "does this
    // list contain X" filtering in application code (see service layer).
    @Builder.Default
    // EAGER: this is a small list (a handful of product types at most), and
    // with spring.jpa.open-in-view=false, Jackson serializes the response
    // AFTER the transaction closes - a LAZY collection here throws
    // LazyInitializationException on every single GET, which is exactly
    // what was happening (500 on every covenant-template list load).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "covenant_template_product_types", joinColumns = @JoinColumn(name = "template_id"))
    @Column(name = "product_type")
    @Enumerated(EnumType.STRING)
    private List<com.bizkredit.monitoring.enums.ProductType> applicableProductTypes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CovenantTemplateStatus status = CovenantTemplateStatus.ACTIVE;

    private Long createdById;
}
