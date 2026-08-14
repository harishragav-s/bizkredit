package com.bizkredit.monitoring.service;

import com.bizkredit.monitoring.client.CollateralGateway;
import com.bizkredit.monitoring.dto.DrawdownDTO;
import com.bizkredit.monitoring.dto.FacilityDTO;
import com.bizkredit.monitoring.enums.*;
import com.bizkredit.monitoring.entity.EarlyWarningSignal;
import com.bizkredit.monitoring.entity.NPARecord;
import com.bizkredit.monitoring.repository.EarlyWarningSignalRepository;
import com.bizkredit.monitoring.repository.NPARecordRepository;
import com.bizkredit.monitoring.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NPAClassificationService {

    private final CollateralGateway collateralGateway;
    private final NPARecordRepository npaRecordRepository;
    private final EarlyWarningSignalRepository ewsRepository;

    @Transactional
    public int runClassification() {
        List<FacilityDTO> facilities = collateralGateway.getFacilitiesByStatus("ACTIVE");
        int classified = 0;

        for (FacilityDTO facility : facilities) {
            // Finds a DISBURSED drawdown whose due date has passed and
            // that hasn't been fully repaid (still DISBURSED, not
            // REPAID) - this is the actual overdue detection. Nothing
            // else in the system ever sets DrawdownStatus.OVERDUE on
            // its own, so checking for that status directly (as this
            // method used to) could never match anything; comparing
            // repaymentDate to today is what makes "the applicant
            // didn't repay in time" actually observable.
            var overdueDrawdown = collateralGateway.getDrawdownsByFacility(facility.getFacilityId())
                    .stream()
                    .filter(d -> "DISBURSED".equals(d.getStatus())
                            && d.getRepaymentDate() != null
                            && d.getRepaymentDate().isBefore(LocalDate.now()))
                    .findFirst();

            if (overdueDrawdown.isEmpty()) {
                continue;
            }

            DrawdownDTO drawdown = overdueDrawdown.get();

            long overdueDays = ChronoUnit.DAYS.between(
                    drawdown.getRepaymentDate(),
                    LocalDate.now()
            );

            // Reflects the real state on the drawdown itself, not just
            // in monitoring-service's own signals - a bare DISBURSED
            // status on a drawdown that's actually 45 days overdue
            // would be misleading anywhere it's displayed (Facility
            // Management, My Facility, etc). collateral-service owns
            // the write - this goes over Feign, not a local cross-schema
            // save.
            collateralGateway.markDrawdownOverdue(facility.getFacilityId(), drawdown.getDrawdownId());

            if (overdueDays <= 30) {
                createSMASignal(facility, EWSSeverity.GREEN);
            } else if (overdueDays <= 60) {
                createSMASignal(facility, EWSSeverity.AMBER);
            } else if (overdueDays <= 90) {
                createSMASignal(facility, EWSSeverity.RED);
            } else {
                classifyAsNPA(facility, (int) overdueDays);
                classified++;
            }
        }

        return classified;
    }

    @Transactional(readOnly = true)
    public List<NPARecord> getAllNPA(
            NPAProvisioningCategory category,
            NPARecordStatus status) {

        if (category != null && status != null) {
            return npaRecordRepository.findByProvisioningCategoryAndStatus(category, status);
        }

        if (status != null) {
            return npaRecordRepository.findByStatus(status);
        }

        return npaRecordRepository.findAll();
    }

    @Transactional
    public NPARecord upgradeNPA(Long npaId) {
        NPARecord record = npaRecordRepository.findById(npaId)
                .orElseThrow(() -> new ResourceNotFoundException("NPA record not found"));

        record.setStatus(NPARecordStatus.UPGRADED);

        // collateral-service owns the facility_account write - over Feign,
        // not a local cross-schema save.
        collateralGateway.updateNpaStatus(record.getFacilityId(), "ACTIVE");

        return npaRecordRepository.save(record);
    }

    private void createSMASignal(FacilityDTO facility, EWSSeverity severity) {
        boolean exists = ewsRepository.findByFacilityId(facility.getFacilityId())
                .stream()
                .anyMatch(e -> e.getSeverity() == severity && e.getStatus() == EWSStatus.OPEN);

        if (exists) {
            return;
        }

        ewsRepository.save(EarlyWarningSignal.builder()
                .facilityId(facility.getFacilityId())
                .signalType(EWSSignalType.DOWNGRADE)
                .severity(severity)
                .detectedDate(LocalDate.now())
                .status(EWSStatus.OPEN)
                .build());
    }

    private void classifyAsNPA(FacilityDTO facility, int overdueDays) {
        boolean exists = npaRecordRepository
                .findByFacilityIdAndStatus(
                        facility.getFacilityId(),
                        NPARecordStatus.ACTIVE
                )
                .isPresent();

        if (exists) {
            return;
        }

        // collateral-service owns the facility_account write - over Feign,
        // not a local cross-schema save.
        collateralGateway.updateNpaStatus(facility.getFacilityId(), "NPA");

        BigDecimal outstanding = facility.getOutstandingBalance() != null
                ? facility.getOutstandingBalance() : BigDecimal.ZERO;

        NPARecord record = NPARecord.builder()
                .facilityId(facility.getFacilityId())
                .classificationDate(LocalDate.now())
                .overdueDays(overdueDays)
                .outstandingAtClassification(outstanding)
                .provisioningCategory(NPAProvisioningCategory.SUB_STANDARD)
                .status(NPARecordStatus.ACTIVE)
                .build();

        npaRecordRepository.save(record);
    }
}
