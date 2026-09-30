package com.company.kra.modules.dashboard.service;

import com.company.kra.common.enums.TaskStatus;
import com.company.kra.common.metrics.TimesheetMetrics;
import com.company.kra.common.enums.WorkLogDayStatus;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.modules.dashboard.dto.EmployeeDashboardResponse;
import com.company.kra.modules.dashboard.dto.ManagerDashboardResponse;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.organisation.entity.CompanySettings;
import com.company.kra.modules.organisation.service.CompanySettingsService;
import com.company.kra.modules.task.repository.TaskRepository;
import com.company.kra.modules.worklog.dto.MissingSubmissionResponse;
import com.company.kra.modules.worklog.dto.WorkLogDaySummaryResponse;
import com.company.kra.modules.worklog.entity.WorkLogDay;
import com.company.kra.modules.worklog.service.WorkLogService;
import com.company.kra.security.AccessScopeResolver;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates for the employee and manager landing pages. All of it is derived
 * from work-log and task data, so the dashboard tells the truth about what has
 * actually been recorded.
 *
 * <p>Which dates were working days is asked of {@link TimesheetMetrics} rather
 * than worked out here. This class once carried its own copy of the rule that
 * checked the working-week setting and nothing else, and so reported public
 * holidays as days people had failed to file and counted them into the week's
 * expected hours.</p>
 */
@Service
@RequiredArgsConstructor
public class WorkDashboardService {

    private static final int TREND_DAYS = 7;
    private static final int LIST_LIMIT = 8;

    private final WorkLogService workLogService;
    private final TaskRepository taskRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanySettingsService settingsService;
    private final AccessScopeResolver accessScopeResolver;
    private final TimesheetMetrics timesheetMetrics;

    @Transactional(readOnly = true)
    public EmployeeDashboardResponse employeeDashboard() {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long employeeId = requireEmployeeId(principal);
        Long companyId = principal.getCompanyId();
        CompanySettings settings = settingsService.settingsFor(companyId);

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate trendStart = today.minusDays(TREND_DAYS - 1L);

        Map<LocalDate, WorkLogDay> trendDays = workLogService.daysByDate(employeeId, trendStart, today);
        WorkLogDay todayRow = trendDays.get(today);

        List<EmployeeDashboardResponse.DayTrendPoint> trend = new ArrayList<>();
        List<LocalDate> unsubmitted = new ArrayList<>();

        for (int offset = TREND_DAYS - 1; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            WorkLogDay row = trendDays.get(date);
            trend.add(new EmployeeDashboardResponse.DayTrendPoint(
                    date,
                    date.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    row == null ? 0 : row.getLoggedMinutes(),
                    row == null ? 0 : row.getProductiveMinutes(),
                    row == null ? WorkLogDayStatus.DRAFT : row.getStatus()));

            // Asked of the company calendar rather than answered here, so a
            // holiday is not reported as a day somebody failed to file.
            boolean workingDay = timesheetMetrics.workingDaysBetween(companyId, date, date) > 0;
            boolean filed = row != null && row.getStatus() != WorkLogDayStatus.DRAFT;
            if (workingDay && !filed && !date.isAfter(today)) {
                unsubmitted.add(date);
            }
        }

        Map<LocalDate, WorkLogDay> weekDays = workLogService.daysByDate(employeeId, weekStart, today);
        int weekLogged = weekDays.values().stream().mapToInt(WorkLogDay::getLoggedMinutes).sum();
        int weekExpected = timesheetMetrics.workingDaysBetween(companyId, weekStart, today)
                * settings.netWorkingMinutes();

        return new EmployeeDashboardResponse(
                today,
                todayRow == null ? WorkLogDayStatus.DRAFT : todayRow.getStatus(),
                todayRow == null ? grossMinutes(settings) : todayRow.getOfficeMinutes(),
                todayRow == null ? 0 : todayRow.getLoggedMinutes(),
                todayRow == null ? 0 : todayRow.getProductiveMinutes(),
                todayRow == null ? grossMinutes(settings) : todayRow.getUnallocatedMinutes(),
                todayRow == null ? 0 : todayRow.getEntryCount(),
                weekLogged,
                weekExpected,
                openTaskCount(employeeId),
                taskRepository.countByAssigneeIdAndStatusAndActiveTrue(employeeId, TaskStatus.COMPLETED),
                taskRepository.countOverdue(employeeId, today),
                dueTodayCount(employeeId, today),
                trend,
                workLogService.categoryBreakdown(employeeId, trendStart, today),
                unsubmitted);
    }

    @Transactional(readOnly = true)
    public ManagerDashboardResponse managerDashboard(LocalDate date) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        LocalDate target = date == null ? LocalDate.now() : date;
        AccessScopeResolver.Scope scope = accessScopeResolver.visibleEmployeeIds(principal);

        List<Employee> team = scope.organisationWide()
                ? employeeRepository.findByCompanyIdAndActiveTrueOrderByFirstNameAsc(principal.getCompanyId())
                : employeeRepository.findAllById(scope.employeeIds()).stream()
                .filter(Employee::isActive)
                .toList();

        // The caller does not review themselves, so they are not part of the count.
        List<Employee> reviewable = team.stream()
                .filter(employee -> !employee.getId().equals(principal.getEmployeeId()))
                .toList();

        List<MissingSubmissionResponse> missing = workLogService.missingSubmissions(target).stream()
                .filter(row -> !row.employeeId().equals(principal.getEmployeeId()))
                .toList();

        var awaiting = workLogService.search(null, WorkLogDayStatus.SUBMITTED, null, null,
                PageRequest.of(0, LIST_LIMIT, Sort.by(Sort.Direction.ASC, "workDate")));

        int teamLogged = 0;
        int teamProductive = 0;
        long openTasks = 0;
        long overdueTasks = 0;
        for (Employee employee : reviewable) {
            WorkLogDay day = workLogService.daysByDate(employee.getId(), target, target).get(target);
            if (day != null) {
                teamLogged += day.getLoggedMinutes();
                teamProductive += day.getProductiveMinutes();
            }
            openTasks += openTaskCount(employee.getId());
            overdueTasks += taskRepository.countOverdue(employee.getId(), LocalDate.now());
        }

        int teamSize = reviewable.size();
        int missingCount = missing.size();
        int submitted = Math.max(teamSize - missingCount, 0);
        double rate = teamSize == 0 ? 0 : Math.round(submitted * 1000.0 / teamSize) / 10.0;

        return new ManagerDashboardResponse(target, teamSize, submitted, missingCount,
                workLogService.pendingReviewCount(), teamLogged, teamProductive, openTasks, overdueTasks,
                rate, missing.stream().limit(LIST_LIMIT).toList(),
                awaiting.content().stream().limit(LIST_LIMIT).toList());
    }

    private long openTaskCount(Long employeeId) {
        return taskRepository.countByAssigneeIdAndActiveTrue(employeeId)
                - taskRepository.countByAssigneeIdAndStatusAndActiveTrue(employeeId, TaskStatus.COMPLETED)
                - taskRepository.countByAssigneeIdAndStatusAndActiveTrue(employeeId, TaskStatus.CANCELLED);
    }

    private long dueTodayCount(Long employeeId, LocalDate today) {
        return taskRepository.search(SecurityUtils.requireCompanyId(), false, Set.of(employeeId),
                        employeeId, null, null, null, null, true, today, null, PageRequest.of(0, 1))
                .getTotalElements();
    }

    private int grossMinutes(CompanySettings settings) {
        return settings.netWorkingMinutes() + settings.getBreakMinutes();
    }

    private Long requireEmployeeId(AppUserPrincipal principal) {
        if (principal.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "This account is not linked to an employee record");
        }
        return principal.getEmployeeId();
    }

    /** Kept so summaries stay one type across dashboards and the queue. */
    List<WorkLogDaySummaryResponse> noSummaries() {
        return List.of();
    }
}
