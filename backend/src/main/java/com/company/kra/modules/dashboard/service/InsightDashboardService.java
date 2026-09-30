package com.company.kra.modules.dashboard.service;

import com.company.kra.audit.AuditLog;
import com.company.kra.audit.AuditLogRepository;
import com.company.kra.common.enums.EmployeeStatus;
import com.company.kra.common.metrics.TimesheetMetrics;
import com.company.kra.common.report.ReportRow;
import com.company.kra.common.report.ReportTable;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.appraisal.service.AppraisalReportData;
import com.company.kra.modules.dashboard.dto.AdminDashboardResponse;
import com.company.kra.modules.dashboard.dto.DepartmentDashboardResponse;
import com.company.kra.modules.dashboard.dto.DepartmentDashboardResponse.BandSlice;
import com.company.kra.modules.dashboard.dto.DepartmentDashboardResponse.PersonScore;
import com.company.kra.modules.dashboard.dto.DepartmentDashboardResponse.TeamScore;
import com.company.kra.modules.dashboard.dto.DepartmentDashboardResponse.TrendPoint;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.organisation.repository.DepartmentRepository;
import com.company.kra.modules.organisation.repository.DesignationRepository;
import com.company.kra.modules.organisation.repository.TeamRepository;
import com.company.kra.modules.organisation.service.DepartmentDirectory;
import com.company.kra.modules.performance.service.JobHealthLookup;
import com.company.kra.modules.performance.service.PerformanceReportData;
import com.company.kra.modules.task.service.TaskReportData;
import com.company.kra.modules.worklog.service.WorkReportData;
import com.company.kra.security.AccessScopeResolver;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The department-head and administrator dashboards.
 *
 * <p>Both are composed from the same ports the reports read, on purpose: a
 * dashboard tile and the report behind it should never disagree, and the only
 * way to guarantee that is to have one of them not compute anything of its
 * own.</p>
 */
@Service
@RequiredArgsConstructor
public class InsightDashboardService {

    private static final int LIST_LIMIT = 5;
    private static final int TREND_MONTHS = 6;

    private final PerformanceReportData performanceData;
    private final WorkReportData workData;
    private final TaskReportData taskData;
    private final AppraisalReportData appraisalData;
    private final JobHealthLookup jobHealth;
    private final TimesheetMetrics timesheetMetrics;
    private final AccessScopeResolver accessScopeResolver;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final TeamRepository teamRepository;
    private final DepartmentDirectory departmentDirectory;
    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public DepartmentDashboardResponse departmentDashboard(Long departmentId, Integer year,
                                                           Integer month) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AccessScopeResolver.Scope scope = accessScopeResolver.visibleEmployeeIds(principal);
        Long companyId = principal.getCompanyId();

        String departmentName = departmentDirectory.nameOf(departmentId);
        if (departmentName == null) {
            throw new ResourceNotFoundException("Department", departmentId);
        }

        LocalDate today = LocalDate.now();
        int perfYear = year == null ? today.getYear() : year;
        Integer perfMonth = month == null ? today.getMonthValue() : month;

        long headcount = employeeRepository.findByCompanyIdAndActiveTrueOrderByFirstNameAsc(companyId)
                .stream()
                .filter(employee -> scope.covers(employee.getId()))
                .filter(employee -> employee.getDepartment() != null
                        && departmentId.equals(employee.getDepartment().getId()))
                .count();

        long managers = employeeRepository.findByCompanyIdAndActiveTrueOrderByFirstNameAsc(companyId)
                .stream()
                .filter(employee -> employee.getDepartment() != null
                        && departmentId.equals(employee.getDepartment().getId()))
                .map(employee -> employee.getReportingManager() == null
                        ? null : employee.getReportingManager().getId())
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();

        LocalDate windowStart = today.withDayOfMonth(1);
        ReportTable timesheet = workData.departmentTimesheet(companyId, scope, windowStart, today,
                departmentId);
        ReportTable todayFiled = workData.departmentTimesheet(companyId, scope, today, today,
                departmentId);

        PerformanceReportData.AverageScore average = performanceData.averageScore(companyId, scope,
                perfYear, perfMonth, departmentId);
        ReportTable departmentAverages = performanceData.departmentPerformance(companyId, scope,
                perfYear, perfMonth);
        TaskReportData.TaskCounts tasks = taskData.counts(companyId, scope, windowStart, today,
                departmentId);
        AppraisalReportData.AppraisalCounts appraisals = appraisalData.counts(companyId, scope,
                departmentId);

        int workingDays = timesheetMetrics.workingDaysBetween(companyId, windowStart, today);
        long expected = headcount * Math.max(workingDays, 1);
        long filed = sum(timesheet, 2);

        return new DepartmentDashboardResponse(departmentId, departmentName,
                periodLabel(perfYear, perfMonth), headcount, managers,
                sum(todayFiled, 2), rate(filed, expected),
                decimal(cell(timesheet, 0, 5)), average.average(), average.scored(),
                columnAverage(departmentAverages, departmentName, 5),
                columnAverage(departmentAverages, departmentName, 6),
                tasks.completed(), tasks.open(), appraisals.inFlight(), appraisals.complete(),
                bands(companyId, scope, perfYear, perfMonth, departmentId),
                teamScores(departmentAverages),
                people(performanceData.ranking(companyId, scope, perfYear, perfMonth, departmentId,
                        false, LIST_LIMIT)),
                people(performanceData.ranking(companyId, scope, perfYear, perfMonth, departmentId,
                        true, LIST_LIMIT)),
                scoreTrend(companyId, scope, perfYear, perfMonth, departmentId));
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse adminDashboard(Integer year, Integer month) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AccessScopeResolver.Scope scope = accessScopeResolver.visibleEmployeeIds(principal);
        Long companyId = principal.getCompanyId();

        LocalDate today = LocalDate.now();
        int perfYear = year == null ? today.getYear() : year;
        Integer perfMonth = month == null ? today.getMonthValue() : month;
        LocalDate windowStart = today.withDayOfMonth(1);

        long activeEmployees = employeeRepository.countByCompanyIdAndStatusAndActiveTrue(companyId,
                EmployeeStatus.ACTIVE);
        long totalEmployees = employeeRepository.countByCompanyIdAndActiveTrue(companyId);

        ReportTable timesheet = workData.departmentTimesheet(companyId, scope, windowStart, today, null);
        ReportTable todayFiled = workData.departmentTimesheet(companyId, scope, today, today, null);
        PerformanceReportData.AverageScore average = performanceData.averageScore(companyId, scope,
                perfYear, perfMonth, null);
        ReportTable departmentAverages = performanceData.departmentPerformance(companyId, scope,
                perfYear, perfMonth);
        AppraisalReportData.AppraisalCounts appraisals = appraisalData.counts(companyId, scope, null);

        int workingDays = timesheetMetrics.workingDaysBetween(companyId, windowStart, today);
        long expected = activeEmployees * Math.max(workingDays, 1);

        List<AdminDashboardResponse.JobRunSummary> jobs = jobHealth.recentRuns(companyId, 6).stream()
                .map(run -> new AdminDashboardResponse.JobRunSummary(run.jobName(), run.periodKey(),
                        run.status(), run.processed(), run.failed(), run.message(),
                        run.startedAt(), run.finishedAt(), run.healthy()))
                .toList();

        List<AdminDashboardResponse.AuditEntrySummary> activity = auditLogRepository
                .search(null, null, null, null, null, null, null, PageRequest.of(0, 8))
                .getContent().stream()
                .map(this::toAuditSummary)
                .toList();

        return new AdminDashboardResponse(periodLabel(perfYear, perfMonth), totalEmployees,
                activeEmployees,
                departmentRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId).size(),
                teamRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId).size(),
                designationRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId).size(),
                sum(todayFiled, 2), rate(sum(timesheet, 2), expected),
                weightedProductiveRatio(timesheet), average.average(), average.scored(),
                appraisals.openCycles(), appraisals.complete(),
                bands(companyId, scope, perfYear, perfMonth, null),
                teamScores(departmentAverages),
                people(performanceData.ranking(companyId, scope, perfYear, perfMonth, null, false,
                        LIST_LIMIT)),
                people(performanceData.ranking(companyId, scope, perfYear, perfMonth, null, true,
                        LIST_LIMIT)),
                submissionTrend(companyId, scope, today),
                jobs, activity);
    }

    // ------------------------------------------------------------- internals

    private List<BandSlice> bands(Long companyId, AccessScopeResolver.Scope scope, int year,
                                  Integer month, Long departmentId) {
        return performanceData.bandDistribution(companyId, scope, year, month, departmentId).stream()
                .map(band -> new BandSlice(band.name(), band.colourHex(), band.count()))
                .toList();
    }

    private List<TeamScore> teamScores(ReportTable departmentAverages) {
        List<TeamScore> scores = new ArrayList<>();
        for (ReportRow row : departmentAverages.rows()) {
            scores.add(new TeamScore(String.valueOf(row.at(0)), decimal(row.at(2)),
                    longValue(row.at(1))));
        }
        return scores;
    }

    private List<PersonScore> people(ReportTable ranking) {
        List<PersonScore> people = new ArrayList<>();
        for (ReportRow row : ranking.rows()) {
            people.add(new PersonScore(text(row.at(1)), text(row.at(2)), text(row.at(3)),
                    decimal(row.at(6)), text(row.at(7))));
        }
        return people;
    }

    /** The last few months' averages, so a tile reads as a direction, not a dot. */
    private List<TrendPoint> scoreTrend(Long companyId, AccessScopeResolver.Scope scope, int year,
                                        Integer month, Long departmentId) {
        List<TrendPoint> points = new ArrayList<>();
        int end = month == null ? 12 : month;
        for (int cursor = Math.max(end - TREND_MONTHS + 1, 1); cursor <= end; cursor++) {
            PerformanceReportData.AverageScore average = performanceData.averageScore(companyId,
                    scope, year, cursor, departmentId);
            points.add(new TrendPoint(
                    Month.of(cursor).getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    average.average()));
        }
        return points;
    }

    /** Submission rate per day over the last fortnight. */
    private List<TrendPoint> submissionTrend(Long companyId, AccessScopeResolver.Scope scope,
                                             LocalDate today) {
        List<TrendPoint> points = new ArrayList<>();
        long headcount = employeeRepository.countByCompanyIdAndActiveTrue(companyId);

        for (int offset = 13; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            if (timesheetMetrics.workingDaysBetween(companyId, date, date) == 0) {
                continue;
            }
            long filed = sum(workData.departmentTimesheet(companyId, scope, date, date, null), 2);
            points.add(new TrendPoint(date.getDayOfMonth() + "/" + date.getMonthValue(),
                    BigDecimal.valueOf(rate(filed, headcount)).setScale(1, RoundingMode.HALF_UP)));
        }
        return points;
    }

    private AdminDashboardResponse.AuditEntrySummary toAuditSummary(AuditLog log) {
        return new AdminDashboardResponse.AuditEntrySummary(log.getActorName(), log.getModule(),
                log.getAction(), log.getEntityType(), log.getEntityId(), log.getReason(),
                log.getCreatedAt());
    }

    /**
     * Productive share across departments, weighted by hours rather than a
     * mean of means: a two-person team should not move the organisation
     * figure as much as a fifty-person one.
     */
    private BigDecimal weightedProductiveRatio(ReportTable timesheet) {
        long logged = 0;
        long productive = 0;
        for (ReportRow row : timesheet.rows()) {
            logged += longValue(row.at(3));
            productive += longValue(row.at(4));
        }
        if (logged == 0) {
            return null;
        }
        return BigDecimal.valueOf(productive).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(logged), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal columnAverage(ReportTable table, String departmentName, int column) {
        for (ReportRow row : table.rows()) {
            if (departmentName.equals(row.at(0))) {
                return decimal(row.at(column));
            }
        }
        return null;
    }

    private long sum(ReportTable table, int column) {
        long total = 0;
        for (ReportRow row : table.rows()) {
            total += longValue(row.at(column));
        }
        return total;
    }

    private Object cell(ReportTable table, int row, int column) {
        return table.rows().size() > row ? table.rows().get(row).at(column) : null;
    }

    private double rate(long part, long whole) {
        return whole <= 0 ? 0
                : BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP)
                        .doubleValue();
    }

    private String periodLabel(int year, Integer month) {
        return month == null ? String.valueOf(year)
                : Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value instanceof Number number
                ? BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP) : null;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
