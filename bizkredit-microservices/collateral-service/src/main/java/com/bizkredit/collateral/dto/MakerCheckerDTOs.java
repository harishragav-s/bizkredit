package com.bizkredit.collateral.dto;

import com.bizkredit.collateral.enums.MakerCheckerAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class MakerCheckerDTOs {

    public record MakerCheckerRequest(
            @NotBlank String entityType,
            Long entityId,
            @NotNull MakerCheckerAction action,
            String payloadJson,
            @NotBlank String requiredCheckerRole
    ) {}

    public record MakerCheckerActionRequest(
            String comments
    ) {}
}
