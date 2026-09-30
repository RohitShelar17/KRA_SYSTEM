package com.company.kra.common.report;

/**
 * What a column holds, so every renderer and the grid format it the same way.
 * A percentage is a number with a suffix, not a string, so it still sorts and
 * sums correctly in an exported spreadsheet.
 */
public enum ColumnType {
    TEXT,
    NUMBER,
    PERCENT,
    SCORE,
    DATE,
    DATETIME,
    /** Stored as whole minutes; rendered as hours. */
    DURATION,
    STATUS
}
