package com.company.kra.common.enums;

/**
 * Drives how a work-log entry rolls into the day totals. Whether an activity
 * is productive is a separate, configurable flag on the category itself.
 */
public enum CategoryType {
    WORK,
    MEETING,
    BREAK,
    ADMIN,
    TRAINING,
    SUPPORT,
    OTHER
}
