package com.bizkredit.credit;

import com.bizkredit.credit.client.SmeGateway;
import com.bizkredit.credit.dto.LoanApplicationDTO;
import com.bizkredit.credit.entity.CreditProposal;
import com.bizkredit.credit.entity.FinancialStatement;
import com.bizkredit.credit.entity.UnderwritingDecision;
import com.bizkredit.credit.repository.CreditProposalRepository;
import com.bizkredit.credit.repository.FinancialStatementRepository;
import com.bizkredit.credit.repository.UnderwritingDecisionRepository;
import com.bizkredit.credit.service.FinancialAnalysisService;
import com.bizkredit.credit.service.ScorecardService;
import com.bizkredit.credit.service.AuditLogService;
import com.bizkredit.credit.service.NotificationHelper;
import com.bizkredit.credit.enums.DecisionStatus;
import com.bizkredit.credit.enums.NotificationCategory;
import com.bizkredit.credit.enums.ProductType;
import com.bizkredit.credit.enums.ProposalStatus;
import com.bizkredit.credit.enums.RiskCategory;
import com.bizkredit.credit.exception.BadRequestException;
import com.bizkredit.credit.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialAnalysisServiceTest {

    @Mock private FinancialStatementRepository statementRepository;
    @Mock private CreditProposalRepository proposalRepository;
    @Mock private UnderwritingDecisionRepository decisionRepository;
    @Mock private SmeGateway smeGateway;
    @Mock private ScorecardService scorecardService;
    @Mock private AuditLogService auditLogService;
    @Mock private NotificationHelper notificationHelper;

    @InjectMocks
    private FinancialAnalysisService financialService;

    private LoanApplicationDTO sampleApplication;
    private CreditProposal sampleProposal;

    @BeforeEach
    void setUp() {
        sampleApplication = LoanApplicationDTO.builder()
                .applicationId(1L)
                .productType(ProductType.TERM_LOAN.name())
                .status("IN_REVIEW")
                .assignedAnalystId(10L)
                .build();

        sampleProposal = CreditProposal.builder()
                .proposalId(1L)
                .applicationId(1L)
                .analystId(2L)
                .computedRatingScore(new BigDecimal("75.0"))
                .riskCategory(RiskCategory.MEDIUM)
                .suggestedAmount(new BigDecimal("900000"))
                .status(ProposalStatus.DRAFT)
                .scorecardAutoComputed(true)
                .build();
    }

    @Test
    void addStatement_autoComputesRatios() {
        FinancialStatement statement = FinancialStatement.builder()
                .financialYear("2023-24")
                .revenue(new BigDecimal("5000000"))
                .ebitda(new BigDecimal("800000"))
                .totalAssets(new BigDecimal("3000000"))
                .totalLiabilities(new BigDecimal("1500000"))
                .netWorth(new BigDecimal("1500000"))
                .build();

        when(smeGateway.getApplication(1L)).thenReturn(sampleApplication);
        when(statementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FinancialStatement saved = financialService.addStatement(1L, statement);

        assertThat(saved.getCurrentRatio()).isNotNull();
        assertThat(saved.getDebtEquityRatio()).isNotNull();
        assertThat(saved.getDscr()).isNotNull();
        assertThat(saved.getApplicationId()).isEqualTo(1L);
        verify(auditLogService).log(any(), eq("CREATE"), eq("FinancialStatement"), any());
    }

    @Test
    void addStatement_applicationNotFound_throwsResourceNotFound() {
        when(smeGateway.getApplication(99L)).thenThrow(new ResourceNotFoundException("Application not found: 99"));

        assertThatThrownBy(() -> financialService.addStatement(99L, new FinancialStatement()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createProposal_success() {
        when(smeGateway.getApplication(1L)).thenReturn(sampleApplication);
        when(proposalRepository.save(any())).thenReturn(sampleProposal);

        CreditProposal saved = financialService.createProposal(1L, sampleProposal);

        assertThat(saved.getStatus()).isEqualTo(ProposalStatus.DRAFT);
        verify(auditLogService).log(any(), eq("CREATE"), eq("CreditProposal"), any());
    }

    @Test
    void submitProposal_success() {
        when(proposalRepository.findById(1L)).thenReturn(Optional.of(sampleProposal));
        when(proposalRepository.save(any())).thenReturn(sampleProposal);

        CreditProposal submitted = financialService.submitProposal(1L);

        assertThat(submitted.getStatus()).isEqualTo(ProposalStatus.SUBMITTED);
    }

    @Test
    void submitProposal_alreadySubmitted_throwsBadRequest() {
        sampleProposal.setStatus(ProposalStatus.SUBMITTED);
        when(proposalRepository.findById(1L)).thenReturn(Optional.of(sampleProposal));

        assertThatThrownBy(() -> financialService.submitProposal(1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already submitted");
    }

    @Test
    void makeDecision_approved_updatesProposalStatus() {
        sampleProposal.setStatus(ProposalStatus.SUBMITTED);

        UnderwritingDecision decision = UnderwritingDecision.builder()
                .managerId(3L)
                .sanctionedAmount(new BigDecimal("900000"))
                .status(DecisionStatus.APPROVED)
                .build();

        when(proposalRepository.findById(1L)).thenReturn(Optional.of(sampleProposal));
        when(smeGateway.getApplication(1L)).thenReturn(sampleApplication);
        when(decisionRepository.save(any())).thenReturn(decision);

        UnderwritingDecision saved = financialService.makeDecision(1L, decision);

        assertThat(saved.getStatus()).isEqualTo(DecisionStatus.APPROVED);
        assertThat(sampleProposal.getStatus()).isEqualTo(ProposalStatus.APPROVED_BY_MANAGER);
        verify(notificationHelper).notify(
                eq(sampleApplication.getAssignedAnalystId()),
                contains("SANCTIONED"),
                eq(NotificationCategory.APPLICATION)
        );
    }

    @Test
    void makeDecision_proposalNotSubmitted_throwsBadRequest() {
        sampleProposal.setStatus(ProposalStatus.DRAFT);
        when(proposalRepository.findById(1L)).thenReturn(Optional.of(sampleProposal));

        assertThatThrownBy(() -> financialService.makeDecision(1L, new UnderwritingDecision()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("SUBMITTED");
    }
}
