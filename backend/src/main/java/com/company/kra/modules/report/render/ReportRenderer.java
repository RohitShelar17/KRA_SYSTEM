package com.company.kra.modules.report.render;

import com.company.kra.common.enums.ReportFormat;
import com.company.kra.common.report.ReportTable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

/**
 * Writes a report in one format.
 *
 * <p>Renderers stream to an {@link OutputStream} rather than returning a byte
 * array, so a large export is bounded by disk rather than by heap.</p>
 */
public interface ReportRenderer {

    ReportFormat format();

    /**
     * @param heading  what the report is called, printed on the export
     * @param filters  the filter set, printed with it so the file is self-describing
     */
    void render(ReportTable table, String heading, Map<String, String> filters, OutputStream out)
            throws IOException;
}
