package com.company.kra.modules.report.render;

import com.company.kra.common.enums.ReportFormat;
import com.company.kra.common.report.ReportColumn;
import com.company.kra.common.report.ReportRow;
import com.company.kra.common.report.ReportTable;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * CSV, streamed a row at a time.
 *
 * <p>Written with a UTF-8 byte-order mark because the usual destination is
 * Excel, which otherwise reads accented names as mojibake.</p>
 */
@Component
public class CsvRenderer implements ReportRenderer {

    @Override
    public ReportFormat format() {
        return ReportFormat.CSV;
    }

    @Override
    public void render(ReportTable table, String heading, Map<String, String> filters,
                       OutputStream out) throws IOException {
        out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        writer.write(escape(heading));
        writer.newLine();
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            writer.write(escape(filter.getKey() + ": " + filter.getValue()));
            writer.newLine();
        }
        writer.newLine();

        writer.write(String.join(",", table.columns().stream()
                .map(ReportColumn::label).map(this::escape).toList()));
        writer.newLine();

        for (ReportRow row : table.rows()) {
            StringBuilder line = new StringBuilder();
            for (int index = 0; index < table.columns().size(); index++) {
                if (index > 0) {
                    line.append(',');
                }
                line.append(escape(value(row.at(index))));
            }
            writer.write(line.toString());
            writer.newLine();
        }
        writer.flush();
    }

    private String value(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        return String.valueOf(value);
    }

    /** Quotes only what needs quoting, and doubles any quote inside the value. */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n")
                || value.contains("\r");
        if (!needsQuotes) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
