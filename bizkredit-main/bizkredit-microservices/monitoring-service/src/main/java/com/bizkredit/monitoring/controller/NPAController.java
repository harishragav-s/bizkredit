package com.bizkredit.monitoring.controller;

import com.bizkredit.monitoring.dto.ApiResponse;
import com.bizkredit.monitoring.service.NPAClassificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Risk Monitoring & Portfolio")
@RestController
@RequestMapping("/api/npa")
@RequiredArgsConstructor
public class NPAController {

    private final NPAClassificationService npaService;

    @PostMapping("/classify")
    @PreAuthorize("hasAnyRole('CREDIT_ANALYST','ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> classify() {
        int count = npaService.runClassification();

        return ResponseEntity.ok(ApiResponse.ok(
                "Classification complete",
                Map.of("newNPAClassified", count)
        ));
    }
}
