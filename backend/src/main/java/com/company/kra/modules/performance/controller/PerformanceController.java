package com.company.kra.modules.performance.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.enums.PerformanceScope;
import com.company.kra.modules.performance.dto.AdjustScoreRequest;
import com.company.kra.modules.performance.dto.PerformanceSnapshotResponse;
import com.company.kra.modules.performance.dto.PerformanceTrendResponse;
import com.company.kra.modules.performance.dto.ReasonRequest;
import com.company.kra.modules.performance.dto.RecalculateRequest;
import com.company.kra.modules.performance.dto.RecalculationSummaryResponse;
import com.company.kra.modules.performance.service.PerformanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Performance", description = "Score snapshots, recalculation and the freeze")
public class PerformanceController {

    private final PerformanceService performanceService;

    // ----------------------------------------------------------------- read

    @GetMapping("/performance/my/daily")
    @PreAuthorize("hasAuthority('PERFORMANCE_READ_SELF')")
    @Operation(summary = "The caller's daily score, with the breakdown behind it")
    public ResponseEntity<ApiResponse<PerformanceSnapshotResponse>> myDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.of(performanceService.daily(null, date)));
    }

    @GetMapping("/performance/my/monthly")
    @PreAuthorize("hasAuthority('PERFORMANCE_READ_SELF')")
    public ResponseEntity<ApiResponse<PerformanceSnapshotResponse>> myMonthly(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(ApiResponse.of(performanceService.monthly(null, year, month)));
    }

    @GetMapping("/performance/my/quarterly")
    @PreAuthorize("hasAuthority('PERFORMANCE_READ_SELF')")
    public ResponseEntity<ApiResponse<PerformanceSnapshotResponse>> myQuarterly(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return ResponseEntity.ok(ApiResponse.of(performanceService.quarterly(null, year, quarter)));
    }

    @GetMapping("/performance/my/annual")
    @PreAuthorize("hasAuthority('PERFORMANCE_READ_SELF')")
    public ResponseEntity<ApiResponse<PerformanceSnapshotResponse>> myAnnual(
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.of(performanceService.annual(null, year)));
    }

    @GetMapping("/performance/my/trend")
    @PreAuthorize("hasAuthority('PERFORMANCE_READ_SELF')")
    @Operation(summary = "The caller's score history for the trend chart")
    public ResponseEntity<ApiResponse<PerformanceTrendResponse>> myTrend(
            @RequestParam(required = false) PerformanceScope scope,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.of(performanceService.trend(null, scope, year)));
    }

    @GetMapping("/employees/{employeeId}/performance/daily")
    @PreAuthorize("hasAnyAuthority('PERFORMANCE_READ_TEAM','PERFORMANCE_READ_ALL')")
    public ResponseEntity<ApiResponse<PerformanceSnapshotResponse>> daily(
            @PathVariable Long employeeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.of(performanceService.daily(employeeId, date)));
    }

    @GetMapping("/employees/{employeeId}/performance/monthly")
    @PreAuthorize("hasAnyAuthority('PERFORMANCE_READ_TEAM','PERFORMANCE_READ_ALL')")
    public ResponseEntity<ApiResponse<PerformanceSnapshotResponse>> monthly(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(ApiResponse.of(performanceService.monthly(employeeId, year, month)));
    }

    @GetMapping("/employees/{employeeId}/performance/quarterly")
    @PreAuthorize("hasAnyAuthority('PERFORMANCE_READ_TEAM','PERFORMANCE_READ_ALL')")
    public ResponseEntity<ApiResponse<PerformanceSnapshotResponse>> quarterly(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer quarter) {
        return ResponseEntity.ok(ApiResponse.of(performanceService.quarterly(employeeId, year, quarter)));
    }

    @GetMapping("/employees/{employeeId}/performance/annual")
    @PreAuthorize("hasAnyAuthority('PERFORMANCE_READ_TEAM','PERFORMANCE_READ_ALL')")
    public ResponseEntity<ApiResponse<PerformanceSnapshotResponse>> annual(
            @PathVariable Long employeeId, @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.of(performanceService.annual(employeeId, year)));
    }

    @GetMapping("/employees/{employeeId}/performance/trend")
    @PreAuthorize("hasAnyAuthority('PERFORMANCE_READ_TEAM','PERFORMANCE_READ_ALL')")
    public ResponseEntity<ApiResponse<PerformanceTrendResponse>> trend(
            @PathVariable Long employeeId,
            @RequestParam(required = false) PerformanceScope scope,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(ApiResponse.of(performanceService.trend(employeeId, scope, year)));
    }

    // --------------------------------------------------------------- change

    @PostMapping("/performance/recalculate")
    @PreAuthorize("hasAnyAuthority('PERFORMANCE_ADJUST','PERFORMANCE_READ_ALL')")
    @Operation(summary = "Re-run the engine for a scope; finalised snapshots are skipped")
    public ResponseEntity<ApiResponse<RecalculationSummaryResponse>> recalculate(
            @Valid @RequestBody RecalculateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(performanceService.recalculate(request),
                "Recalculation complete"));
    }

    @PostMapping("/performance/{scope}/{id}/adjust")
    @PreAuthorize("hasAuthority('PERFORMANCE_ADJUST')")
    @Operation(summary = "Manually correct a score; a reason is mandatory and is audited")
    public ResponseEntity<ApiResponse<PerformanceSnapshotResponse>> adjust(
            @PathVariable PerformanceScope scope, @PathVariable Long id,
            @Valid @RequestBody AdjustScoreRequest request) {
        return ResponseEntity.ok(ApiResponse.of(performanceService.adjust(scope, id, request),
                "Score adjusted"));
    }

    @PostMapping("/performance/{scope}/{id}/finalize")
    @PreAuthorize("hasAuthority('PERFORMANCE_FINALIZE')")
    @Operation(summary = "Freeze a score; it can no longer be recomputed or adjusted")
    public ResponseEntity<ApiResponse<PerformanceSnapshotResponse>> finalise(
            @PathVariable PerformanceScope scope, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(performanceService.finalise(scope, id),
                "Score finalised"));
    }

    @PostMapping("/performance/{scope}/{id}/reopen")
    @PreAuthorize("hasAuthority('PERFORMANCE_REOPEN')")
    @Operation(summary = "Unfreeze a finalised score; a reason is mandatory and is audited")
    public ResponseEntity<ApiResponse<PerformanceSnapshotResponse>> reopen(
            @PathVariable PerformanceScope scope, @PathVariable Long id,
            @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.of(performanceService.reopen(scope, id, request.reason()),
                "Score reopened"));
    }
}
