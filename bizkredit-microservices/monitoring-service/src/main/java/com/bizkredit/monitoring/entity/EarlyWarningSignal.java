package com.bizkredit.monitoring.entity;

import com.bizkredit.monitoring.enums.EWSSeverity;
import com.bizkredit.monitoring.enums.EWSSignalType;
import com.bizkredit.monitoring.enums.EWSStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "early_warning_signal")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class EarlyWarningSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ewsId;

    // Owned by collateral-service - id only, fetched over Feign
    // (CollateralGateway) wherever the full facility is needed.
    @Column(name = "facility_id", nullable = false)
    private Long facilityId;

    @Enumerated(EnumType.STRING)
    private EWSSignalType signalType;

    @Enumerated(EnumType.STRING)
    private EWSSeverity severity;

    @Builder.Default
    private LocalDate detectedDate = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private EWSStatus status = EWSStatus.OPEN;
}
