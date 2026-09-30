package com.company.kra.common.enums;

import java.util.Set;

/**
 * The inputs a formula may weigh.
 *
 * <p>Which of these appear, and at what weight, is configuration. This enum
 * only says what the engine knows how to measure, so an administrator cannot
 * configure a parameter nothing can compute.</p>
 */
public enum PerformanceParameter {

    TASK_COMPLETION("Tasks completed against tasks assigned"),
    KRA_ACHIEVEMENT("Weighted achievement across assigned KRAs"),
    KPI_ACHIEVEMENT("Weighted achievement across assigned KPIs"),
    PRODUCTIVITY("Productive hours against expected hours"),
    QUALITY_RATING("Quality rating recorded by the manager"),
    MANAGER_RATING("Overall rating recorded by the manager"),
    DEPARTMENT_HEAD_RATING("Rating recorded by the department head"),
    SELF_ASSESSMENT("The employee's own rating"),
    DAILY_AVERAGE("Average of the daily scores in the period"),
    MONTHLY_AVERAGE("Average of the monthly scores in the period"),
    QUARTERLY_AVERAGE("Average of the quarterly scores in the year");

    private final String description;

    PerformanceParameter(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** Parameters that make sense at a given grain. */
    public static Set<PerformanceParameter> validFor(PerformanceScope scope) {
        return switch (scope) {
            case DAILY -> Set.of(TASK_COMPLETION, KRA_ACHIEVEMENT, KPI_ACHIEVEMENT, PRODUCTIVITY,
                    QUALITY_RATING, MANAGER_RATING);
            case MONTHLY -> Set.of(DAILY_AVERAGE, TASK_COMPLETION, KRA_ACHIEVEMENT, KPI_ACHIEVEMENT,
                    PRODUCTIVITY, QUALITY_RATING, MANAGER_RATING);
            case QUARTERLY -> Set.of(MONTHLY_AVERAGE, KRA_ACHIEVEMENT, KPI_ACHIEVEMENT,
                    MANAGER_RATING, DEPARTMENT_HEAD_RATING);
            case ANNUAL -> Set.of(QUARTERLY_AVERAGE, KRA_ACHIEVEMENT, KPI_ACHIEVEMENT,
                    MANAGER_RATING, DEPARTMENT_HEAD_RATING, SELF_ASSESSMENT);
        };
    }
}
