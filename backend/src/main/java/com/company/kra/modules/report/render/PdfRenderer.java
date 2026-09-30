package com.company.kra.modules.report.render;

import com.company.kra.common.enums.ReportFormat;
import com.company.kra.common.report.ColumnType;
import com.company.kra.common.report.ReportColumn;
import com.company.kra.common.report.ReportRow;
import com.company.kra.common.report.ReportTable;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * PDF, for the version that gets printed and taken into a review meeting.
 *
 * <p>The filter set is printed under the title, so a page that has left the
 * system still says what it is a report of. Wide reports are laid out
 * landscape, because a table that runs off the page is not a report.</p>
 */
@Component
public class PdfRenderer implements ReportRenderer {

    private static final int WIDE_COLUMN_COUNT = 8;
    private static final int MAX_ROWS = 5_000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    @Override
    public ReportFormat format() {
        return ReportFormat.PDF;
    }

    @Override
    public void render(ReportTable table, String heading, Map<String, String> filters,
                       OutputStream out) {
        Rectangle page = table.columns().size() > WIDE_COLUMN_COUNT
                ? PageSize.A4.rotate() : PageSize.A4;

        Document document = new Document(page, 28, 28, 30, 30);
        PdfWriter.getInstance(document, out);
        document.open();

        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, new Color(17, 24, 39));
        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 8.5f, new Color(100, 116, 139));
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(30, 41, 59));

        Paragraph title = new Paragraph(heading, titleFont);
        title.setSpacingAfter(4);
        document.add(title);

        StringBuilder meta = new StringBuilder("Generated " + LocalDateTime.now().format(STAMP));
        filters.forEach((key, value) -> meta.append("   ·   ").append(key).append(": ").append(value));
        meta.append("   ·   ").append(table.size()).append(" rows");
        Paragraph filterLine = new Paragraph(meta.toString(), metaFont);
        filterLine.setSpacingAfter(10);
        document.add(filterLine);

        PdfPTable grid = new PdfPTable(table.columns().size());
        grid.setWidthPercentage(100);
        grid.setHeaderRows(1);

        for (ReportColumn column : table.columns()) {
            PdfPCell cell = new PdfPCell(new Phrase(column.label(), headerFont));
            cell.setBackgroundColor(new Color(67, 56, 202));
            cell.setPadding(5);
            cell.setBorderColor(new Color(224, 231, 255));
            grid.addCell(cell);
        }

        int written = 0;
        for (ReportRow row : table.rows()) {
            if (written++ >= MAX_ROWS) {
                break;
            }
            for (int index = 0; index < table.columns().size(); index++) {
                ReportColumn column = table.columns().get(index);
                PdfPCell cell = new PdfPCell(new Phrase(text(row.at(index), column), cellFont));
                cell.setPadding(4);
                cell.setBorderColor(new Color(226, 232, 240));
                if (numeric(column.type())) {
                    cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                }
                grid.addCell(cell);
            }
        }

        document.add(grid);

        if (table.size() > MAX_ROWS) {
            // Saying so beats quietly handing somebody a partial document.
            Paragraph note = new Paragraph("Showing the first " + MAX_ROWS + " of " + table.size()
                    + " rows. Export to XLSX or CSV for the complete set.", metaFont);
            note.setSpacingBefore(8);
            document.add(note);
        }

        document.close();
    }

    private boolean numeric(ColumnType type) {
        return type == ColumnType.NUMBER || type == ColumnType.PERCENT
                || type == ColumnType.SCORE || type == ColumnType.DURATION;
    }

    private String text(Object value, ReportColumn column) {
        if (value == null) {
            return "—";
        }
        return switch (column.type()) {
            case DURATION -> value instanceof Number minutes
                    ? String.format("%.1f h", minutes.doubleValue() / 60.0) : String.valueOf(value);
            case PERCENT -> decimal(value) + "%";
            case SCORE -> decimal(value);
            default -> String.valueOf(value);
        };
    }

    private String decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number number) {
            return String.format("%.2f", number.doubleValue());
        }
        return String.valueOf(value);
    }
}
