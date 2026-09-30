package com.company.kra.common.enums;

/**
 * The shared filter vocabulary. A report declares which of these it accepts,
 * and the same frontend filter panel drives every report screen because the
 * vocabulary is fixed rather than per-report.
 */
public enum ReportFilterKey {
    EMPLOYEE_ID,
    DEPARTMENT_ID,
    MANAGER_ID,
    DESIGNATION_ID,
    FROM_DATE,
    TO_DATE,
    YEAR,
    MONTH,
    QUARTER,
    RATING_BAND_ID,
    CYCLE_ID,
    STATUS,
    LIMIT
}
