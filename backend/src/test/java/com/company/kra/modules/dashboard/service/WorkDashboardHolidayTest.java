package com.company.kra.modules.dashboard.service;

import com.company.kra.common.enums.DataScope;
import com.company.kra.common.metrics.TimesheetMetrics;
import com.company.kra.modules.dashboard.dto.EmployeeDashboardResponse;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.organisation.entity.CompanySettings;
import com.company.kra.modules.organisation.service.CompanySettingsService;
import com.company.kra.modules.task.entity.Task;
import com.company.kra.modules.task.repository.TaskRepository;
import com.company.kra.modules.worklog.service.WorkLogService;
import com.company.kra.security.AccessScopeResolver;
import com.company.kra.security.AppUserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The dashboard must not invent its own idea of a working day.
 *
 * <p>It used to: a private helper checked the working-week setting and stopped
 * there, so a public holiday was reported as a day somebody had failed to file
 * and was counted into the week's expected hours. Both figures are things a
 * person gets asked about, which is why this is pinned down rather than left to
 * the next reader to notice.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkDashboardHolidayTest {

    private static final Long COMPANY = 1L;
    private static final Long EMPLOYEE = 10L;
    private static final int NET_MINUTES = 480;

    @Mock private WorkLogService workLogService;
    @Mock private TaskRepository taskRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private CompanySettingsService settingsService;
    @Mock private AccessScopeResolver accessScopeResolver;
    @Mock private TimesheetMetrics timesheetMetrics;

    private WorkDashboardService service;

    /** Somewhere inside the seven-day trend window, whatever day the test runs. */
    private final LocalDate holiday = LocalDate.now().minusDays(2);

    @BeforeEach
    void setUp() {
        service = new WorkDashboardService(workLogService, taskRepository, employeeRepository,
                settingsService, accessScopeResolver, timesheetMetrics);

        when(settingsService.settingsFor(anyLong())).thenReturn(settings());
        when(workLogService.daysByDate(anyLong(), any(), any())).thenReturn(Map.of());
        when(workLogService.categoryBreakdown(anyLong(), any(), any())).thenReturn(List.of());
        when(taskRepository.countByAssigneeIdAndActiveTrue(anyLong())).thenReturn(0L);
        when(taskRepository.countByAssigneeIdAndStatusAndActiveTrue(anyLong(), any())).thenReturn(0L);
        when(taskRepository.countOverdue(anyLong(), any())).thenReturn(0L);
        when(taskRepository.search(any(), anyBoolean(), any(), any(), any(), any(), any(), any(),
                anyBoolean(), any(), any(), any())).thenReturn(new PageImpl<Task>(List.of()));

        // Every day in the window is a working day except the holiday, so a
        // weekend cannot make the assertion below pass for the wrong reason.
        when(timesheetMetrics.workingDaysBetween(eq(COMPANY), any(), any()))
                .thenAnswer(call -> {
                    LocalDate from = call.getArgument(1);
                    LocalDate to = call.getArgument(2);
                    int days = 0;
                    for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
                        if (!cursor.equals(holiday)) {
                            days++;
                        }
                    }
                    return days;
                });

        authenticate();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A holiday is not reported as a day somebody failed to file")
    void holidayIsNotUnsubmitted() {
        EmployeeDashboardResponse dashboard = service.employeeDashboard();

        assertThat(dashboard.unsubmittedDates()).doesNotContain(holiday);
        // The rest of the window is still chased: the guard must not have
        // silenced the whole list.
        assertThat(dashboard.unsubmittedDates()).contains(LocalDate.now().minusDays(3));
    }

    @Test
    @DisplayName("Expected hours for the week exclude the holiday")
    void weekExpectedExcludesTheHoliday() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        long daysInWeekSoFar = weekStart.datesUntil(today.plusDays(1)).count();
        boolean holidayFallsInThisWeek = !holiday.isBefore(weekStart) && !holiday.isAfter(today);
        long expectedWorkingDays = daysInWeekSoFar - (holidayFallsInThisWeek ? 1 : 0);

        EmployeeDashboardResponse dashboard = service.employeeDashboard();

        assertThat(dashboard.weekExpectedMinutes()).isEqualTo((int) expectedWorkingDays * NET_MINUTES);
    }

    @Test
    @DisplayName("The working-day question is asked of the calendar, not answered locally")
    void delegatesToTheCalendar() {
        EmployeeDashboardResponse dashboard = service.employeeDashboard();

        // With the calendar insisting nothing is a working day, there is
        // nothing outstanding and nothing expected — which is only possible if
        // the dashboard is reading the answer rather than deciding for itself.
        when(timesheetMetrics.workingDaysBetween(anyLong(), any(), any())).thenReturn(0);
        EmployeeDashboardResponse closed = service.employeeDashboard();

        assertThat(dashboard.unsubmittedDates()).isNotEmpty();
        assertThat(closed.unsubmittedDates()).isEmpty();
        assertThat(closed.weekExpectedMinutes()).isZero();
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal(), null, Set.of()));
    }

    private AppUserPrincipal principal() {
        return AppUserPrincipal.builder()
                .userId(EMPLOYEE)
                .employeeId(EMPLOYEE)
                .companyId(COMPANY)
                .departmentId(2L)
                .username("tester")
                .email("tester@company.com")
                .displayName("Priya Nair")
                .passwordHash("hash")
                .enabled(true)
                .accountLocked(false)
                .mustChangePassword(false)
                .dataScope(DataScope.SELF)
                .roles(Set.of("EMPLOYEE"))
                .permissions(Set.of("DASHBOARD_VIEW", "WORKLOG_WRITE_SELF"))
                .build();
    }

    private CompanySettings settings() {
        CompanySettings settings = new CompanySettings();
        settings.setWorkStartTime(LocalTime.of(9, 0));
        settings.setWorkEndTime(LocalTime.of(18, 0));
        settings.setBreakMinutes(60);
        settings.setWorkingDays("MON,TUE,WED,THU,FRI");
        return settings;
    }
}
