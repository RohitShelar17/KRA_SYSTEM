package com.company.kra.common.enums;

import java.time.LocalDate;

public enum PeriodType {
    DAILY,
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    ANNUAL;

    /** How many of this period fit in a year, used to validate a period index. */
    public int periodsPerYear() {
        return switch (this) {
            case DAILY -> 366;
            case WEEKLY -> 53;
            case MONTHLY -> 12;
            case QUARTERLY -> 4;
            case ANNUAL -> 1;
        };
    }

    public LocalDate startOf(int year, int index) {
        return switch (this) {
            case MONTHLY -> LocalDate.of(year, index, 1);
            case QUARTERLY -> LocalDate.of(year, (index - 1) * 3 + 1, 1);
            case ANNUAL -> LocalDate.of(year, 1, 1);
            case WEEKLY -> LocalDate.of(year, 1, 1).plusWeeks(index - 1L);
            case DAILY -> LocalDate.ofYearDay(year, index);
        };
    }

    public LocalDate endOf(int year, int index) {
        return switch (this) {
            case MONTHLY -> startOf(year, index).plusMonths(1).minusDays(1);
            case QUARTERLY -> startOf(year, index).plusMonths(3).minusDays(1);
            case ANNUAL -> LocalDate.of(year, 12, 31);
            case WEEKLY -> startOf(year, index).plusDays(6);
            case DAILY -> startOf(year, index);
        };
    }

    /** Human label for a period, e.g. "Q3 2026" or "September 2026". */
    public String label(int year, int index) {
        return switch (this) {
            case MONTHLY -> startOf(year, index).getMonth().getDisplayName(
                    java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + year;
            case QUARTERLY -> "Q" + index + " " + year;
            case ANNUAL -> String.valueOf(year);
            case WEEKLY -> "Week " + index + ", " + year;
            case DAILY -> startOf(year, index).toString();
        };
    }
}
