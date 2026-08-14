package com.bizkredit.collateral;

import com.bizkredit.collateral.client.CreditGateway;
import com.bizkredit.collateral.client.SmeLoanGateway;
import com.bizkredit.collateral.dto.LoanApplicationDTO;
import com.bizkredit.collateral.dto.SMEBusinessDTO;
import com.bizkredit.collateral.dto.UnderwritingDecisionDTO;
import com.bizkredit.collateral.entity.CollateralRecord;
import com.bizkredit.collateral.entity.CollateralRevaluation;
import com.bizkredit.collateral.entity.Drawdown;
import com.bizkredit.collateral.entity.FacilityAccount;
import com.bizkredit.collateral.repository.CollateralRecordRepository;
import com.bizkredit.collateral.repository.CollateralRevaluationRepository;
import com.bizkredit.collateral.repository.DrawdownRepository;
import com.bizkredit.collateral.repository.FacilityAccountRepository;
import com.bizkredit.collateral.repository.WorkingCapitalUtilisationRepository;
import com.bizkredit.collateral.service.CollateralFacilityService;
import com.bizkredit.collateral.service.NotificationHelper;
import com.bizkredit.collateral.enums.AssetType;
import com.bizkredit.collateral.enums.CollateralStatus;
import com.bizkredit.collateral.enums.DrawdownStatus;
import com.bizkredit.collateral.enums.FacilityStatus;
import com.bizkredit.collateral.enums.ProductType;
import com.bizkredit.collateral.exception.BadRequestException;
import com.bizkredit.collateral.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollateralFacilityServiceTest {

    @Mock private CollateralRecordRepository collateralRepository;
    @Mock private CollateralRevaluationRepository revaluationRepository;
    @Mock private FacilityAccountRepository facilityRepository;
    @Mock private DrawdownRepository drawdownRepository;
    @Mock private WorkingCapitalUtilisationRepository utilisationRepository;
    @Mock private SmeLoanGateway smeLoanGateway;
    @Mock private CreditGateway creditGateway;
    @Mock private NotificationHelper notificationHelper;

    @InjectMocks
    private CollateralFacilityService service;

    private LoanApplicationDTO sampleApplication;
    private SMEBusinessDTO sampleBusiness;
    private CollateralRecord sampleCollateral;
    private FacilityAccount sampleFacility;

    @BeforeEach
    void setUp() {
        sampleApplication = LoanApplicationDTO.builder()
                .applicationId(1L)
                .applicantUserId(50L)
                .status("SANCTIONED")
                .build();

        sampleBusiness = SMEBusinessDTO.builder()
                .businessId(1L)
                .businessName("Affrina Enterprises")
                .industry("Manufacturing")
                .build();

        sampleCollateral = CollateralRecord.builder()
                .collateralId(1L)
                .applicationId(1L)
                .assetType(AssetType.PROPERTY)
                .marketValue(new BigDecimal("5000000"))
                .forceValuePercent(new BigDecimal("70"))
                .status(CollateralStatus.REGISTERED)
                .build();

        sampleFacility = FacilityAccount.builder()
                .facilityId(1L)
                .applicationId(1L)
                .businessId(1L)
                .productType(ProductType.TERM_LOAN)
                .sanctionedLimit(new BigDecimal("3000000"))
                .disbursedAmount(BigDecimal.ZERO)
                .outstandingBalance(BigDecimal.ZERO)
                .status(FacilityStatus.ACTIVE)
                .build();
    }

    @Test
    void registerCollateral_autoComputesRealisableValue() {
        when(smeLoanGateway.getApplication(1L)).thenReturn(sampleApplication);
        when(collateralRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CollateralRecord saved = service.registerCollateral(1L, sampleCollateral);

        assertThat(saved.getRealisableValue()).isEqualByComparingTo(new BigDecimal("3500000.00"));
    }

    @Test
    void registerCollateral_applicationNotFound_throwsResourceNotFound() {
        when(smeLoanGateway.getApplication(99L)).thenThrow(new ResourceNotFoundException("Application not found: 99"));

        assertThatThrownBy(() -> service.registerCollateral(99L, sampleCollateral))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createFacility_success() {
        UnderwritingDecisionDTO decision = UnderwritingDecisionDTO.builder()
                .decisionId(1L)
                .sanctionedAmount(new BigDecimal("3000000"))
                .decisionDate(LocalDate.now())
                .status("APPROVED")
                .build();

        when(smeLoanGateway.getApplication(1L)).thenReturn(sampleApplication);
        when(smeLoanGateway.getBusiness(1L)).thenReturn(sampleBusiness);
        when(creditGateway.getLatestDecisionForApplication(1L)).thenReturn(Optional.of(decision));
        when(facilityRepository.save(any())).thenReturn(sampleFacility);

        FacilityAccount saved = service.createFacility(1L, 1L, sampleFacility);

        assertThat(saved.getStatus()).isEqualTo(FacilityStatus.ACTIVE);
        assertThat(saved.getDisbursedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void createFacility_noUnderwritingDecision_throwsBadRequest() {
        when(smeLoanGateway.getApplication(1L)).thenReturn(sampleApplication);
        when(smeLoanGateway.getBusiness(1L)).thenReturn(sampleBusiness);
        when(creditGateway.getLatestDecisionForApplication(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createFacility(1L, 1L, sampleFacility))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No underwriting decision found");
    }

    @Test
    void requestDrawdown_exceedsLimit_throwsBadRequest() {
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(sampleFacility));

        Drawdown drawdown = Drawdown.builder().amount(new BigDecimal("5000000")).purpose("Equipment").build();

        assertThatThrownBy(() -> service.requestDrawdown(1L, drawdown))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("exceeds available limit");
    }

    @Test
    void requestDrawdown_success() {
        when(facilityRepository.findById(1L)).thenReturn(Optional.of(sampleFacility));
        when(drawdownRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Drawdown drawdown = Drawdown.builder().amount(new BigDecimal("1000000")).purpose("Working capital").build();

        Drawdown saved = service.requestDrawdown(1L, drawdown);

        assertThat(saved.getStatus()).isEqualTo(DrawdownStatus.REQUESTED);
    }

    @Test
    void disburseDrawdown_success_updatesBalance() {
        Drawdown drawdown = Drawdown.builder()
                .drawdownId(1L).facility(sampleFacility)
                .amount(new BigDecimal("1000000")).status(DrawdownStatus.APPROVED).build();

        when(drawdownRepository.findById(1L)).thenReturn(Optional.of(drawdown));
        when(drawdownRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(smeLoanGateway.getApplication(1L)).thenReturn(sampleApplication);

        Drawdown disbursed = service.disburseDrawdown(1L);

        assertThat(disbursed.getStatus()).isEqualTo(DrawdownStatus.DISBURSED);
        assertThat(sampleFacility.getOutstandingBalance()).isEqualByComparingTo(new BigDecimal("1000000"));
    }

    @Test
    void revalueCollateral_computesChangePercent() {
        when(collateralRepository.findById(1L)).thenReturn(Optional.of(sampleCollateral));
        when(collateralRepository.save(any())).thenReturn(sampleCollateral);
        when(revaluationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CollateralRevaluation rev = service.revalueCollateral(1L, new BigDecimal("6000000"), 5L);

        assertThat(rev.getChangePercent()).isEqualByComparingTo(new BigDecimal("20.0000"));
    }
}
