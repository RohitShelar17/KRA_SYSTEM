package com.company.kra.modules.report.definition;

import com.company.kra.common.enums.ReportFilterKey;
import com.company.kra.common.report.ReportTable;

import java.util.Set;

/**
 * One report.
 *
 * <p>Adding a report is a new implementation of this interface registered as a
 * bean — not a new controller, a new endpoint or a new frontend screen. The
 * registry finds it, the shared filter panel drives it and every renderer can
 * already export it.</p>
 */
public interface ReportDefinition {

    /** Stable identifier used in the URL and stored on every export request. */
    String key();

    String title();

    String description();

    /** Heading the report is listed under. */
    String group();

    /**
     * The permission a caller needs on top of REPORT_VIEW. A report over one
     * person's own timesheet asks less than one ranking the whole organisation.
     */
    String requiredPermission();

    /** Which of the shared filters this report understands. */
    Set<ReportFilterKey> filters();

    /**
     * Runs the report within the caller's scope. Implementations return every
     * matching row; paging for display and streaming for export are the
     * service's job, not each report's.
     */
    ReportTable run(ReportContext context);
}
