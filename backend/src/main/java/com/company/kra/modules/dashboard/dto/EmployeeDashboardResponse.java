package com.company.kra.modules.dashboard.dto;

import com.company.kra.common.enums.WorkLogDayStatus;
import com.company.kra.modules.worklog.dto.CategoryBreakdownResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * The employee landing page. Every figure here comes from work actually
 * recorded; nothing is stubbed for a phase that has not shipped.
 */
public record EmployeeDashboardResponse(
        LocalDate today,
        WorkLogDayStatus todayStatus,
        int officeMinutes,
        int todayLoggedMinutes,
        int todayProductiveMinutes,
        int todayUnallocatedMinutes,
        int todayEntryCount,
        int weekLoggedMinutes,
        int weekExpectedMinutes,
        long openTasks,
        long completedTasks,
        long overdueTasks,
        long tasksDueToday,
        List<DayTrendPoint> lastDays,
        List<CategoryBreakdownResponse> categoryBreakdown,
        List<LocalDate> unsubmittedDates) {

    public record DayTrendPoint(LocalDate date, String label, int loggedMinutes, int productiveMinutes,
                                WorkLogDayStatus status) {
    }
}
