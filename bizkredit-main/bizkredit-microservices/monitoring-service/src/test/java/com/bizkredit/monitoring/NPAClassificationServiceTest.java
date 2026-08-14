package com.bizkredit.monitoring;

import com.bizkredit.monitoring.client.CollateralGateway;
import com.bizkredit.monitoring.dto.DrawdownDTO;
import com.bizkredit.monitoring.dto.FacilityDTO;
import com.bizkredit.monitoring.entity.EarlyWarningSignal;
import com.bizkredit.monitoring.entity.NPARecord;
import com.bizkredit.monitoring.repository.EarlyWarningSignalRepository;
import com.bizkredit.monitoring.repository.NPARecordRepository;
import com.bizkredit.monitoring.service.NPAClassificationService;
import com.bizkredit.monitoring.enums.*;
import com.bizkredit.monitoring.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NPAClassificationServiceTest {

    @Mock private CollateralGateway collateralGateway;
    @Mock private NPARecordRepository npaRecordRepository;
    @Mock private EarlyWarningSignalRepository ewsRepository;

    @InjectMocks
    private NPAClassificationService service;

    private FacilityDTO sampleFacility;

    @BeforeEach
    void setUp() {
        sampleFacility = FacilityDTO.builder()
                .facilityId(1L)
                .sanctionedLimit(new BigDecimal("2000000"))
                .outstandingBalance(new BigDecimal("1500000"))
                .status("ACTIVE")
                .build();
    }

    @Test
    void runClassification_noOverdueDrawdowns_classifiesNothing() {
        when(collateralGateway.getFacilitiesByStatus("ACTIVE"))
                .thenReturn(List.of(sampleFacility));
        when(collateralGateway.getDrawdownsByFacility(1L))
                .thenReturn(List.of());

        int count = service.runClassification();

        assertThat(count).isZero();
        verify(npaRecordRepository, never()).save(any());
    }

    @Test
    void runClassification_over90Days_classifiesAsNPA() {
        DrawdownDTO overdueDrawdown = DrawdownDTO.builder()
                .drawdownId(1L)
                .amount(new BigDecimal("1000000"))
                .status("DISBURSED")
                .repaymentDate(LocalDate.now().minusDays(100))
                .build();

        when(collateralGateway.getFacilitiesByStatus("ACTIVE"))
                .thenReturn(List.of(sampleFacility));
        when(collateralGateway.getDrawdownsByFacility(1L))
                .thenReturn(List.of(overdueDrawdown));
        when(npaRecordRepository.findByFacilityIdAndStatus(
                1L, NPARecordStatus.ACTIVE))
                .thenReturn(Optional.empty());

        int count = service.runClassification();

        assertThat(count).isEqualTo(1);
        verify(collateralGateway).updateNpaStatus(1L, "NPA");
        verify(collateralGateway).markDrawdownOverdue(1L, 1L);
        verify(npaRecordRepository).save(any(NPARecord.class));
    }

    @Test
    void runClassification_30to60Days_createsAmberEWSSignal() {
        DrawdownDTO overdueDrawdown = DrawdownDTO.builder()
                .drawdownId(2L)
                .amount(new BigDecimal("500000"))
                .status("DISBURSED")
                .repaymentDate(LocalDate.now().minusDays(45))
                .build();

        when(collateralGateway.getFacilitiesByStatus("ACTIVE"))
                .thenReturn(List.of(sampleFacility));
        when(collateralGateway.getDrawdownsByFacility(1L))
                .thenReturn(List.of(overdueDrawdown));
        when(ewsRepository.findByFacilityId(1L))
                .thenReturn(List.of());

        int count = service.runClassification();

        assertThat(count).isZero();
        verify(ewsRepository).save(any(EarlyWarningSignal.class));
        verify(npaRecordRepository, never()).save(any());
    }

    @Test
    void runClassification_existingNPARecord_skipsReclassification() {

        DrawdownDTO overdueDrawdown = DrawdownDTO.builder()
                .drawdownId(3L)
                .amount(new BigDecimal("1000000"))
                .status("DISBURSED")
                .repaymentDate(LocalDate.now().minusDays(95))
                .build();

        NPARecord existingRecord = NPARecord.builder()
                .npaId(1L)
                .facilityId(1L)
                .status(NPARecordStatus.ACTIVE)
                .provisioningCategory(NPAProvisioningCategory.SUB_STANDARD)
                .build();

        when(collateralGateway.getFacilitiesByStatus("ACTIVE"))
                .thenReturn(List.of(sampleFacility));
        when(collateralGateway.getDrawdownsByFacility(1L))
                .thenReturn(List.of(overdueDrawdown));
        when(npaRecordRepository.findByFacilityIdAndStatus(
                1L, NPARecordStatus.ACTIVE))
                .thenReturn(Optional.of(existingRecord));

        int count = service.runClassification();

        // "count" tracks how many facilities crossed the 90+ day threshold
        // in this run, not how many NEW records were created - matches
        // current service behavior (classified++ happens regardless of
        // whether classifyAsNPA() short-circuits on an existing record).
        assertThat(count).isEqualTo(1);

        // Still ensure no duplicate record is created
        verify(npaRecordRepository, never()).save(any());
    }

    @Test
    void upgradeNPA_success_setsStatusUpgradedAndReactivatesFacility() {
        NPARecord npaRecord = NPARecord.builder()
                .npaId(1L)
                .facilityId(1L)
                .status(NPARecordStatus.ACTIVE)
                .provisioningCategory(NPAProvisioningCategory.SUB_STANDARD)
                .build();

        when(npaRecordRepository.findById(1L))
                .thenReturn(Optional.of(npaRecord));
        when(npaRecordRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        NPARecord upgraded = service.upgradeNPA(1L);

        assertThat(upgraded.getStatus())
                .isEqualTo(NPARecordStatus.UPGRADED);

        verify(collateralGateway).updateNpaStatus(1L, "ACTIVE");
    }

    @Test
    void upgradeNPA_notFound_throwsResourceNotFoundException() {
        when(npaRecordRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upgradeNPA(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("NPA record not found");
    }
}
