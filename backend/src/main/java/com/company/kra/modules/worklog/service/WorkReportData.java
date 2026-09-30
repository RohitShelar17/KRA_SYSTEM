package com.company.kra.modules.worklog.service;

import com.company.kra.common.report.ReportTable;
import com.company.kra.security.AccessScopeResolver;

import java.time.LocalDate;

/**
 * Timesheet data shaped for reporting.
 *
 * <p>The report module owns no SQL of its own. It asks the module that owns
 * the data, which is what keeps a reporting change from quietly becoming a
 * second, divergent definition of "productive hours".</p>
 *
 * <p>Every method takes the caller's scope and applies it inside the query, so
 * a report cannot return rows the caller is not allowed to see.</p>
 */
public interface WorkReportData {

    /** Every work-log entry in the window: the raw record behind every total. */
    ReportTable dailyWork(Long companyId, AccessScopeResolver.Scope scope, LocalDate from,
                          LocalDate to, Long employeeId, Long departmentId);

    /** One row per employee per day, with the day's totals and its status. */
    ReportTable employeeTimesheet(Long companyId, AccessScopeResolver.Scope scope, LocalDate from,
                                  LocalDate to, Long employeeId, Long departmentId);

    /** The same figures rolled up to departments. */
    ReportTable departmentTimesheet(Long companyId, AccessScopeResolver.Scope scope, LocalDate from,
                                    LocalDate to, Long departmentId);

    /** Productive share of logged time, per employee. */
    ReportTable productivity(Long companyId, AccessScopeResolver.Scope scope, LocalDate from,
                             LocalDate to, Long employeeId, Long departmentId);

    /** Working days in the window that were never filed. */
    ReportTable pendingWorkLogs(Long companyId, AccessScopeResolver.Scope scope, LocalDate from,
                                LocalDate to, Long departmentId);

    /**
     * Who has no filed day for a date. Used by the nightly reminder, so it
     * returns ids rather than a display table.
     */
    java.util.Set<Long> employeesWithoutLog(Long companyId, LocalDate date);
}
