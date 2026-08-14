package com.bizkredit.monitoring.controller;

import com.bizkredit.monitoring.dto.ApiResponse;
import com.bizkredit.monitoring.entity.CovenantTemplate;
import com.bizkredit.monitoring.enums.CovenantType;
import com.bizkredit.monitoring.enums.ProductType;
import com.bizkredit.monitoring.service.CovenantTemplateService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// BP2-43/52 - Covenant Template Library API
@Tag(name = "Risk Monitoring & Portfolio")
@RestController
@RequestMapping("/api/covenant-templates")
@RequiredArgsConstructor
public class CovenantTemplateController {

    private final CovenantTemplateService templateService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<CovenantTemplate>>> getTemplates(
            @RequestParam(required = false) CovenantType covenantType,
            @RequestParam(required = false) ProductType productType) {
        return ResponseEntity.ok(ApiResponse.ok("Covenant templates fetched",
                templateService.getTemplates(covenantType, productType)));
    }
}
