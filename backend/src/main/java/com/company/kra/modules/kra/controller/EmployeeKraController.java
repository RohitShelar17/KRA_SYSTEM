package com.company.kra.modules.kra.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.dto.OptionDto;
import com.company.kra.common.enums.PeriodType;
import com.company.kra.modules.kra.dto.AssignKraRequest;
import com.company.kra.modules.kra.dto.KpiActualRequest;
import com.company.kra.modules.kra.dto.KpiHistoryResponse;
import com.company.kra.modules.kra.dto.KpiProgressResponse;
import com.company.kra.modules.kra.dto.KpiTargetRequest;
import com.company.kra.modules.kra.dto.KraScorecardResponse;
import com.company.kra.modules.kra.dto.WeightageUpdateRequest;
import com.company.kra.modules.kra.service.EmployeeKraService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "KRA assignment", description = "Scorecards, targets and achievement")
public class EmployeeKraController {

    private final EmployeeKraService employeeKraService;

    @GetMapping("/kras/my")
    @PreAuthorize("hasAuthority('KRA_READ_SELF')")
    @Operation(summary = "The caller's own scorecard for a period")
    public ResponseEntity<ApiResponse<KraScorecardResponse>> mine(
            @RequestParam(required = false) PeriodType periodType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer periodIndex) {
        return ResponseEntity.ok(ApiResponse.of(
                employeeKraService.scorecard(null, periodType, year, periodIndex)));
    }

    @GetMapping("/kras/active")
    @PreAuthorize("hasAuthority('KRA_READ_SELF')")
    @Operation(summary = "KRAs active on a date, for attributing work-log time")
    public ResponseEntity<ApiResponse<List<OptionDto>>> active(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.of(employeeKraService.activeOptions(date)));
    }

    @GetMapping("/employees/{employeeId}/kras")
    @PreAuthorize("hasAnyAuthority('KRA_READ_TEAM','KRA_ASSIGN')")
    @Operation(summary = "Someone else's scorecard, within the caller's access scope")
    public ResponseEntity<ApiResponse<KraScorecardResponse>> forEmployee(
            @PathVariable Long employeeId,
            @RequestParam(required = false) PeriodType periodType,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer periodIndex) {
        return ResponseEntity.ok(ApiResponse.of(
                employeeKraService.scorecard(employeeId, periodType, year, periodIndex)));
    }

    @PostMapping("/employees/{employeeId}/kras/assign")
    @PreAuthorize("hasAuthority('KRA_ASSIGN')")
    @Operation(summary = "Copy a published template onto an employee for one period")
    public ResponseEntity<ApiResponse<KraScorecardResponse>> assign(
            @PathVariable Long employeeId, @Valid @RequestBody AssignKraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(employeeKraService.assign(employeeId, request), "KRAs assigned"));
    }

    @PutMapping("/employees/{employeeId}/kras/weightages")
    @PreAuthorize("hasAuthority('KRA_ASSIGN')")
    @Operation(summary = "Adjust weightages as a set; the total must stay at 100%")
    public ResponseEntity<ApiResponse<KraScorecardResponse>> updateWeightages(
            @PathVariable Long employeeId, @Valid @RequestBody WeightageUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(
                employeeKraService.updateWeightages(employeeId, request), "Weightages updated"));
    }

    @PostMapping("/employee-kpis/{kpiId}/targets")
    @PreAuthorize("hasAuthority('KPI_MANAGE')")
    @Operation(summary = "Set or replace the target for a period")
    public ResponseEntity<ApiResponse<KpiProgressResponse>> setTarget(
            @PathVariable Long kpiId, @Valid @RequestBody KpiTargetRequest request) {
        return ResponseEntity.ok(ApiResponse.of(employeeKraService.setTarget(kpiId, request),
                "Target saved"));
    }

    @PostMapping("/employee-kpis/{kpiId}/actuals")
    @PreAuthorize("hasAuthority('KPI_MANAGE')")
    @Operation(summary = "Record an actual value; refused for automatically measured KPIs")
    public ResponseEntity<ApiResponse<KpiProgressResponse>> recordActual(
            @PathVariable Long kpiId, @Valid @RequestBody KpiActualRequest request) {
        return ResponseEntity.ok(ApiResponse.of(employeeKraService.recordActual(kpiId, request),
                "Actual recorded"));
    }

    @GetMapping("/employee-kpis/{kpiId}/history")
    @PreAuthorize("hasAnyAuthority('KRA_READ_SELF','KRA_READ_TEAM')")
    @Operation(summary = "Target against actual for every period recorded")
    public ResponseEntity<ApiResponse<List<KpiHistoryResponse>>> history(@PathVariable Long kpiId) {
        return ResponseEntity.ok(ApiResponse.of(employeeKraService.kpiHistory(kpiId)));
    }
}
