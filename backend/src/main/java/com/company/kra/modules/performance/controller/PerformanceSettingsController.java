package com.company.kra.modules.performance.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.enums.PerformanceScope;
import com.company.kra.modules.performance.dto.FormulaConfigRequest;
import com.company.kra.modules.performance.dto.FormulaConfigResponse;
import com.company.kra.modules.performance.dto.FormulaParameterResponse;
import com.company.kra.modules.performance.dto.RatingBandRequest;
import com.company.kra.modules.performance.dto.RatingBandResponse;
import com.company.kra.modules.performance.service.FormulaConfigService;
import com.company.kra.modules.performance.service.RatingBandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Performance settings", description = "The scoring formula and rating bands")
public class PerformanceSettingsController {

    private final FormulaConfigService formulaService;
    private final RatingBandService bandService;

    @GetMapping("/formula")
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE','PERFORMANCE_READ_ALL')")
    @Operation(summary = "Every formula version, newest first per grain")
    public ResponseEntity<ApiResponse<List<FormulaConfigResponse>>> formulas() {
        return ResponseEntity.ok(ApiResponse.of(formulaService.list()));
    }

    @GetMapping("/formula/active")
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE','PERFORMANCE_READ_SELF')")
    @Operation(summary = "The configuration the engine is using right now")
    public ResponseEntity<ApiResponse<FormulaConfigResponse>> active(
            @RequestParam PerformanceScope scope) {
        return ResponseEntity.ok(ApiResponse.of(formulaService.active(scope)));
    }

    @GetMapping("/formula/parameters")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Parameters the engine can measure at a given grain")
    public ResponseEntity<ApiResponse<List<FormulaParameterResponse>>> parameters(
            @RequestParam PerformanceScope scope) {
        return ResponseEntity.ok(ApiResponse.of(formulaService.availableParameters(scope)));
    }

    @GetMapping("/formula/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<ApiResponse<FormulaConfigResponse>> formula(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(formulaService.get(id)));
    }

    @PostMapping("/formula")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Save a new version and activate it; weights must total 100%")
    public ResponseEntity<ApiResponse<FormulaConfigResponse>> saveFormula(
            @Valid @RequestBody FormulaConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(formulaService.saveNewVersion(request),
                        "New formula version activated"));
    }

    @GetMapping("/rating-bands")
    @PreAuthorize("hasAnyAuthority('SETTINGS_MANAGE','PERFORMANCE_READ_SELF')")
    public ResponseEntity<ApiResponse<List<RatingBandResponse>>> bands() {
        return ResponseEntity.ok(ApiResponse.of(bandService.list()));
    }

    @PutMapping("/rating-bands")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Replace all bands; they must be contiguous with no gap or overlap")
    public ResponseEntity<ApiResponse<List<RatingBandResponse>>> saveBands(
            @Valid @RequestBody List<RatingBandRequest> requests) {
        return ResponseEntity.ok(ApiResponse.of(bandService.replaceAll(requests), "Rating bands saved"));
    }
}
