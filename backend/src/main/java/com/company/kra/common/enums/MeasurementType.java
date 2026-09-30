package com.company.kra.common.enums;

/** How a KPI is expressed. Drives formatting and, for BOOLEAN, the scoring rule. */
public enum MeasurementType {
    PERCENTAGE,
    NUMERIC,
    CURRENCY,
    HOURS,
    COUNT,
    BOOLEAN,
    RATING,
    CUSTOM
}
