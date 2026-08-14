package com.bizkredit.collateral.client;

import com.bizkredit.collateral.dto.CreditProposalDTO;
import com.bizkredit.collateral.dto.UnderwritingDecisionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreditGateway {

    private final CreditServiceClient client;

    public Optional<UnderwritingDecisionDTO> getLatestDecisionForApplication(Long applicationId) {
        List<CreditProposalDTO> proposals;
        try {
            var body = client.getProposalsByApplication(applicationId);
            proposals = (body == null || body.getData() == null) ? List.of() : body.getData();
        } catch (Exception e) {
            log.error("credit-service call failed listing proposals for application {}: {}",
                    applicationId, e.getMessage());
            throw new IllegalStateException(
                    "Could not reach credit-service to load proposals for application " + applicationId, e);
        }

        return proposals.stream()
                .map(p -> fetchDecision(p.getProposalId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.comparing(UnderwritingDecisionDTO::getDecisionDate));
    }

    /** A proposal with no decision yet is a normal, expected state - not an error. */
    private Optional<UnderwritingDecisionDTO> fetchDecision(Long proposalId) {
        try {
            var body = client.getDecisionByProposal(proposalId);
            return (body == null || body.getData() == null) ? Optional.empty() : Optional.of(body.getData());
        } catch (Exception e) {
            log.debug("No decision yet for proposal {} (or credit-service unreachable): {}",
                    proposalId, e.getMessage());
            return Optional.empty();
        }
    }
}
