package com.company.kra.modules.kra.service;

import com.company.kra.common.enums.PeriodType;
import com.company.kra.common.report.ReportColumn;
import com.company.kra.common.report.ReportRow;
import com.company.kra.common.report.ReportTable;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.kra.dto.KpiProgressResponse;
import com.company.kra.modules.kra.dto.KraProgressResponse;
import com.company.kra.modules.kra.dto.KraScorecardResponse;
import com.company.kra.security.AccessScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Flattens scorecards into report rows.
 *
 * <p>It walks the employees in scope and asks for each scorecard, which is one
 * query set per person rather than one clever join. That is a deliberate
 * trade: the achievement rules — direction, cap, exclusion and weight
 * redistribution — stay in one place, and the row cap keeps the cost
 * bounded.</p>
 */
@Service
@RequiredArgsConstructor
public class KraReportDataProvider implements KraReportData {

    private final EmployeeKraService kraService;
    private final EmployeeRepository employeeRepository;

    @Override
    @Transactional(readOnly = true)
    public ReportTable kraPerformance(Long companyId, AccessScopeResolver.Scope scope,
                                      PeriodType periodType, int year, int periodIndex,
                                      Long employeeId, Long departmentId) {
        List<ReportColumn> columns = List.of(
                ReportColumn.text("employeeCode", "Code"),
                ReportColumn.text("employeeName", "Employee"),
                ReportColumn.text("department", "Department"),
                ReportColumn.text("period", "Period"),
                ReportColumn.text("kra", "Key result area"),
                ReportColumn.percent("weightage", "Weight"),
                ReportColumn.percent("achievement", "Achievement"),
                ReportColumn.number("kpisScored", "KPIs scored"),
                ReportColumn.number("kpiCount", "KPIs"),
                ReportColumn.status("status", "Status"),
                ReportColumn.percent("overall", "Scorecard overall"));

        List<ReportRow> rows = new ArrayList<>();
        for (Employee employee : targets(companyId, scope, employeeId, departmentId)) {
            KraScorecardResponse card = kraService.scorecard(employee.getId(), periodType, year,
                    periodIndex);
            for (KraProgressResponse kra : card.kras()) {
                rows.add(ReportRow.of(employee.getEmployeeCode(), employee.fullName(),
                        departmentOf(employee), card.periodLabel(), kra.name(), kra.weightage(),
                        kra.scored() ? kra.achievementPercent() : null, kra.scoredKpiCount(),
                        kra.totalKpiCount(), kra.status(), card.overallAchievement()));
            }
        }
        return ReportTable.of(columns, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportTable kpiAchievement(Long companyId, AccessScopeResolver.Scope scope,
                                      PeriodType periodType, int year, int periodIndex,
                                      Long employeeId, Long departmentId) {
        List<ReportColumn> columns = List.of(
                ReportColumn.text("employeeCode", "Code"),
                ReportColumn.text("employeeName", "Employee"),
                ReportColumn.text("period", "Period"),
                ReportColumn.text("kra", "Key result area"),
                ReportColumn.text("kpi", "KPI"),
                ReportColumn.text("measurement", "Measured in"),
                ReportColumn.text("direction", "Better when"),
                ReportColumn.number("target", "Target"),
                ReportColumn.number("actual", "Actual"),
                ReportColumn.percent("achievement", "Achievement"),
                ReportColumn.text("source", "Actual from"),
                ReportColumn.text("excludedReason", "Excluded because"));

        List<ReportRow> rows = new ArrayList<>();
        for (Employee employee : targets(companyId, scope, employeeId, departmentId)) {
            KraScorecardResponse card = kraService.scorecard(employee.getId(), periodType, year,
                    periodIndex);
            for (KraProgressResponse kra : card.kras()) {
                for (KpiProgressResponse kpi : kra.kpis()) {
                    rows.add(ReportRow.of(employee.getEmployeeCode(), employee.fullName(),
                            card.periodLabel(), kra.name(), kpi.name(), kpi.measurementType(),
                            kpi.targetDirection(), kpi.targetValue(), kpi.actualValue(),
                            kpi.scored() ? kpi.achievementPercent() : null,
                            kpi.derived() ? "Measured automatically" : "Entered",
                            kpi.excludedReason()));
                }
            }
        }
        return ReportTable.of(columns, rows);
    }

    // ------------------------------------------------------------- internals

    private List<Employee> targets(Long companyId, AccessScopeResolver.Scope scope, Long employeeId,
                                   Long departmentId) {
        return employeeRepository.findByCompanyIdAndActiveTrueOrderByFirstNameAsc(companyId).stream()
                .filter(employee -> scope.covers(employee.getId()))
                .filter(employee -> employeeId == null || employeeId.equals(employee.getId()))
                .filter(employee -> departmentId == null || (employee.getDepartment() != null
                        && departmentId.equals(employee.getDepartment().getId())))
                .toList();
    }

    private String departmentOf(Employee employee) {
        return employee.getDepartment() == null ? null : employee.getDepartment().getName();
    }
}
