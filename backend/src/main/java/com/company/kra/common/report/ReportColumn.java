package com.company.kra.common.report;

/** One column of a report: its key, its heading and how to format it. */
public record ReportColumn(String key, String label, ColumnType type) {

    public static ReportColumn text(String key, String label) {
        return new ReportColumn(key, label, ColumnType.TEXT);
    }

    public static ReportColumn number(String key, String label) {
        return new ReportColumn(key, label, ColumnType.NUMBER);
    }

    public static ReportColumn percent(String key, String label) {
        return new ReportColumn(key, label, ColumnType.PERCENT);
    }

    public static ReportColumn score(String key, String label) {
        return new ReportColumn(key, label, ColumnType.SCORE);
    }

    public static ReportColumn date(String key, String label) {
        return new ReportColumn(key, label, ColumnType.DATE);
    }

    public static ReportColumn duration(String key, String label) {
        return new ReportColumn(key, label, ColumnType.DURATION);
    }

    public static ReportColumn status(String key, String label) {
        return new ReportColumn(key, label, ColumnType.STATUS);
    }
}
