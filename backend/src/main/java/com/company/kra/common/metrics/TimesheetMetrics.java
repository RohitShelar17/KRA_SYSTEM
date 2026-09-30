package com.company.kra.common.metrics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What recorded time looks like over a window.
 *
 * <p>Declared here rather than in either consumer: the KRA module reads it to
 * measure a productivity KPI and the performance module reads it to score a
 * period, and neither should reach into the work-log module's repositories to
 * do so. The work-log module implements it, next to the data.</p>
 */
public interface TimesheetMetrics {

    /** Logged and productive minutes, plus how many days were actually filed. */
    record TimeSummary(int loggedMinutes, int productiveMinutes, int submittedDays) {
    }

    TimeSummary summarise(Long employeeId, LocalDate from, LocalDate to);

    /** Productive minutes as a percentage of logged minutes, or null if nothing was logged. */
    BigDecimal productiveHoursRatioPercent(Long employeeId, LocalDate from, LocalDate to);

    /** Working days in the window per the company calendar, ignoring holidays and week-offs. */
    int workingDaysBetween(Long companyId, LocalDate from, LocalDate to);

    /** Contracted minutes for one working day for this employee. */
    int expectedMinutesPerDay(Long employeeId);
}
