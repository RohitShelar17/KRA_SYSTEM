package com.company.kra.modules.report.definitions;

import com.company.kra.common.enums.ReportFilterKey;
import com.company.kra.common.enums.TaskStatus;
import com.company.kra.modules.report.definition.ReportContext;
import com.company.kra.modules.report.definition.ReportDefinition;
import com.company.kra.modules.report.definition.SimpleReport;
import com.company.kra.modules.task.service.TaskReportData;
import com.company.kra.modules.worklog.service.WorkReportData;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.Set;

/**
 * The six reports over recorded work.
 *
 * <p>Each one is a declaration: what it is called, who may run it, which
 * filters it understands, and which module answers it. No report holds a query
 * of its own.</p>
 */
@Configuration
@RequiredArgsConstructor
public class TimesheetReports {

    private static final String GROUP = "Work tracking";

    private final WorkReportData workData;
    private final TaskReportData taskData;

    @Bean
    public ReportDefinition dailyWorkReport() {
        return new SimpleReport("daily-work", "Daily work log",
                "Every work-log entry in the window, with its time slot, category and status.",
                GROUP, "WORKLOG_READ_TEAM",
                Set.of(ReportFilterKey.EMPLOYEE_ID, ReportFilterKey.DEPARTMENT_ID,
                        ReportFilterKey.FROM_DATE, ReportFilterKey.TO_DATE),
                context -> workData.dailyWork(context.companyId(), context.scope(),
                        from(context), to(context),
                        context.longValue(ReportFilterKey.EMPLOYEE_ID),
                        context.longValue(ReportFilterKey.DEPARTMENT_ID)));
    }

    @Bean
    public ReportDefinition employeeTimesheetReport() {
        return new SimpleReport("employee-timesheet", "Employee timesheet",
                "One row per person per day: office hours against logged, productive and "
                        + "unallocated time.",
                GROUP, "WORKLOG_READ_TEAM",
                Set.of(ReportFilterKey.EMPLOYEE_ID, ReportFilterKey.DEPARTMENT_ID,
                        ReportFilterKey.FROM_DATE, ReportFilterKey.TO_DATE),
                context -> workData.employeeTimesheet(context.companyId(), context.scope(),
                        from(context), to(context),
                        context.longValue(ReportFilterKey.EMPLOYEE_ID),
                        context.longValue(ReportFilterKey.DEPARTMENT_ID)));
    }

    @Bean
    public ReportDefinition departmentTimesheetReport() {
        return new SimpleReport("department-timesheet", "Department timesheet",
                "The same hours rolled up to departments, with each one's productive share.",
                GROUP, "WORKLOG_READ_TEAM",
                Set.of(ReportFilterKey.DEPARTMENT_ID, ReportFilterKey.FROM_DATE,
                        ReportFilterKey.TO_DATE),
                context -> workData.departmentTimesheet(context.companyId(), context.scope(),
                        from(context), to(context),
                        context.longValue(ReportFilterKey.DEPARTMENT_ID)));
    }

    @Bean
    public ReportDefinition productivityReport() {
        return new SimpleReport("productivity", "Productivity",
                "Productive time as a share of everything logged, per person.",
                GROUP, "WORKLOG_READ_TEAM",
                Set.of(ReportFilterKey.EMPLOYEE_ID, ReportFilterKey.DEPARTMENT_ID,
                        ReportFilterKey.FROM_DATE, ReportFilterKey.TO_DATE),
                context -> workData.productivity(context.companyId(), context.scope(),
                        from(context), to(context),
                        context.longValue(ReportFilterKey.EMPLOYEE_ID),
                        context.longValue(ReportFilterKey.DEPARTMENT_ID)));
    }

    @Bean
    public ReportDefinition pendingWorkLogsReport() {
        return new SimpleReport("pending-worklogs", "Unfiled work logs",
                "Working days nobody filed, per person, with the dates. Today is excluded "
                        + "because it is not over yet.",
                GROUP, "WORKLOG_READ_TEAM",
                Set.of(ReportFilterKey.DEPARTMENT_ID, ReportFilterKey.FROM_DATE,
                        ReportFilterKey.TO_DATE),
                context -> workData.pendingWorkLogs(context.companyId(), context.scope(),
                        from(context), to(context),
                        context.longValue(ReportFilterKey.DEPARTMENT_ID)));
    }

    @Bean
    public ReportDefinition taskCompletionReport() {
        return new SimpleReport("task-completion", "Task completion",
                "Tasks with their estimate, actual time and whether they landed on time.",
                GROUP, "TASK_READ_TEAM",
                Set.of(ReportFilterKey.EMPLOYEE_ID, ReportFilterKey.DEPARTMENT_ID,
                        ReportFilterKey.FROM_DATE, ReportFilterKey.TO_DATE,
                        ReportFilterKey.STATUS),
                context -> taskData.taskCompletion(context.companyId(), context.scope(),
                        from(context), to(context),
                        context.longValue(ReportFilterKey.EMPLOYEE_ID),
                        context.longValue(ReportFilterKey.DEPARTMENT_ID),
                        status(context)));
    }

    // ------------------------------------------------------------- internals

    /** Defaults to the start of the current month, so a report never runs unbounded. */
    private LocalDate from(ReportContext context) {
        return context.date(ReportFilterKey.FROM_DATE, LocalDate.now().withDayOfMonth(1));
    }

    private LocalDate to(ReportContext context) {
        return context.date(ReportFilterKey.TO_DATE, LocalDate.now());
    }

    private TaskStatus status(ReportContext context) {
        String value = context.text(ReportFilterKey.STATUS);
        return value == null ? null : TaskStatus.valueOf(value);
    }
}
