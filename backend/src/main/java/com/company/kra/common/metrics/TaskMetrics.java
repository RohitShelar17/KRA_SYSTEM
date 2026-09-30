package com.company.kra.common.metrics;

import java.math.BigDecimal;
import java.time.LocalDate;

/** What the task board says about delivery over a window. */
public interface TaskMetrics {

    record TaskSummary(long assigned, long completed, long onTime) {
    }

    TaskSummary summarise(Long employeeId, LocalDate from, LocalDate to);

    /** Completed as a percentage of assigned, or null if none were assigned. */
    BigDecimal completionRatePercent(Long employeeId, LocalDate from, LocalDate to);

    /** Of the tasks completed, the percentage closed by their due date. */
    BigDecimal onTimeRatePercent(Long employeeId, LocalDate from, LocalDate to);
}
