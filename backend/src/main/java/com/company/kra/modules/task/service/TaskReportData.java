package com.company.kra.modules.task.service;

import com.company.kra.common.enums.TaskStatus;
import com.company.kra.common.report.ReportTable;
import com.company.kra.security.AccessScopeResolver;

import java.time.LocalDate;

/** Task data shaped for reporting, scoped inside the query. */
public interface TaskReportData {

    ReportTable taskCompletion(Long companyId, AccessScopeResolver.Scope scope, LocalDate from,
                               LocalDate to, Long employeeId, Long departmentId, TaskStatus status);

    /** Task totals for a slice of the organisation, for the dashboards. */
    TaskCounts counts(Long companyId, AccessScopeResolver.Scope scope, LocalDate from, LocalDate to,
                      Long departmentId);

    /** Completed, still open, and of those how many are past their due date. */
    record TaskCounts(long completed, long open, long overdue) {
    }

    /** Tasks due tomorrow or already overdue, for the reminder job. */
    java.util.List<TaskReminder> dueOrOverdue(Long companyId, LocalDate today);

    record TaskReminder(Long taskId, String title, Long assigneeId, Long managerId,
                        LocalDate dueDate, String status, boolean overdue) {
    }
}
