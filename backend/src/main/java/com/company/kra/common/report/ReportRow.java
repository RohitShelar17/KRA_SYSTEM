package com.company.kra.common.report;

import java.util.Arrays;
import java.util.List;

/**
 * One row, positionally aligned to the table's columns.
 *
 * <p>Values stay typed — a number is a Number, a date is a LocalDate — so the
 * spreadsheet renderer can write real cell types rather than strings that a
 * user then has to convert.</p>
 */
public record ReportRow(List<Object> values) {

    public static ReportRow of(Object... values) {
        return new ReportRow(Arrays.asList(values));
    }

    public Object at(int index) {
        return index < values.size() ? values.get(index) : null;
    }
}
