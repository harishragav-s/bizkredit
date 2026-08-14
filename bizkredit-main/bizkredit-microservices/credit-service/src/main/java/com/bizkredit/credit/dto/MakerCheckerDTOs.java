package com.bizkredit.credit.dto;

import com.bizkredit.credit.enums.MakerCheckerAction;
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
