package com.bizkredit.monitoring.entity;

import com.bizkredit.monitoring.enums.NPAProvisioningCategory;
import com.bizkredit.monitoring.enums.NPARecordStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "npa_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class NPARecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long npaId;

    // Owned by collateral-service - id only, fetched over Feign
    // (CollateralGateway) wherever the full facility is needed.
    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Builder.Default
    private LocalDate classificationDate = LocalDate.now();

    private Integer overdueDays;

    private BigDecimal outstandingAtClassification;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NPAProvisioningCategory provisioningCategory = NPAProvisioningCategory.SUB_STANDARD;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private NPARecordStatus status = NPARecordStatus.ACTIVE;
}
