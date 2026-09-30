package com.company.kra.modules.dashboard.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.modules.dashboard.dto.EmployeeDashboardResponse;
import com.company.kra.modules.dashboard.dto.FoundationOverviewResponse;
import com.company.kra.modules.dashboard.dto.AdminDashboardResponse;
import com.company.kra.modules.dashboard.dto.DepartmentDashboardResponse;
import com.company.kra.modules.dashboard.dto.ManagerDashboardResponse;
import com.company.kra.modules.dashboard.service.DashboardService;
import com.company.kra.modules.dashboard.service.InsightDashboardService;
import com.company.kra.modules.dashboard.service.WorkDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Role dashboards")
public class DashboardController {

    private final DashboardService dashboardService;
    private final WorkDashboardService workDashboardService;
    private final InsightDashboardService insightDashboardService;

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "Organisation shape and the caller's access scope")
    public ResponseEntity<ApiResponse<FoundationOverviewResponse>> overview() {
        return ResponseEntity.ok(ApiResponse.of(dashboardService.overview()));
    }

    @GetMapping("/employee")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    @Operation(summary = "The caller's own day, week and task position")
    public ResponseEntity<ApiResponse<EmployeeDashboardResponse>> employee() {
        return ResponseEntity.ok(ApiResponse.of(workDashboardService.employeeDashboard()));
    }

    @GetMapping("/manager")
    @PreAuthorize("hasAuthority('WORKLOG_READ_TEAM')")
    @Operation(summary = "Team submission compliance and the review queue")
    public ResponseEntity<ApiResponse<ManagerDashboardResponse>> manager(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) java.time.LocalDate date) {
        return ResponseEntity.ok(ApiResponse.of(workDashboardService.managerDashboard(date)));
    }

    @GetMapping("/department/{departmentId}")
    @PreAuthorize("hasAnyAuthority('PERFORMANCE_READ_TEAM','PERFORMANCE_READ_ALL')")
    @Operation(summary = "A department's people, hours, scores and appraisal progress")
    public ResponseEntity<ApiResponse<DepartmentDashboardResponse>> department(
            @PathVariable Long departmentId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(ApiResponse.of(
                insightDashboardService.departmentDashboard(departmentId, year, month)));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyAuthority('PERFORMANCE_READ_ALL','SETTINGS_MANAGE')")
    @Operation(summary = "The organisation view, including scheduler health and recent audit events")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> admin(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        return ResponseEntity.ok(ApiResponse.of(insightDashboardService.adminDashboard(year, month)));
    }
}
