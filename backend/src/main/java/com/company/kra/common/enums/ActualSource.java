package com.company.kra.common.enums;

/**
 * Where a KPI's actual value comes from. Anything other than MANUAL is
 * derived from work already recorded, so the number is evidence rather than
 * an assertion.
 */
public enum ActualSource {
    MANUAL,
    WORK_LOG_PRODUCTIVE_HOURS,
    TASK_COMPLETION_RATE,
    TASK_ON_TIME_RATE,
    INTEGRATION;

    public boolean isDerived() {
        return this != MANUAL && this != INTEGRATION;
    }
}
