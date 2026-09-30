package com.company.kra.common.report;

import java.util.List;

/**
 * A report's result: its columns and its rows together.
 *
 * <p>Columns travel with the rows rather than being declared separately,
 * because a report whose headings and values can drift apart is worse than no
 * report at all.</p>
 */
public record ReportTable(List<ReportColumn> columns, List<ReportRow> rows) {

    public static ReportTable of(List<ReportColumn> columns, List<ReportRow> rows) {
        return new ReportTable(columns, rows);
    }

    public static ReportTable empty(List<ReportColumn> columns) {
        return new ReportTable(columns, List.of());
    }

    public int size() {
        return rows.size();
    }
}
