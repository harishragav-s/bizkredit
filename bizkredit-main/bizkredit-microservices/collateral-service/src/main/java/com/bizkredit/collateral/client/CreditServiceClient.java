package com.bizkredit.collateral.client;

import com.bizkredit.collateral.dto.ApiResponse;
import com.bizkredit.collateral.dto.CreditProposalDTO;
import com.bizkredit.collateral.dto.UnderwritingDecisionDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;


@FeignClient(name = "credit-service", configuration = com.bizkredit.collateral.config.FeignClientConfig.class)
public interface CreditServiceClient {

    @GetMapping("/api/loan-applications/{appId}/credit-proposals")
    ApiResponse<List<CreditProposalDTO>> getProposalsByApplication(@PathVariable("appId") Long applicationId);

    // Real endpoint returns a single decision per proposal (not a list) -
    // credit-service enforces one decision per proposal.
    @GetMapping("/api/credit-proposals/{proposalId}/decisions")
    ApiResponse<UnderwritingDecisionDTO> getDecisionByProposal(@PathVariable("proposalId") Long proposalId);
}
