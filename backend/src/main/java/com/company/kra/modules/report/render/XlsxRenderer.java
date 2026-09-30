package com.company.kra.modules.report.render;

import com.company.kra.common.enums.ReportFormat;
import com.company.kra.common.report.ColumnType;
import com.company.kra.common.report.ReportColumn;
import com.company.kra.common.report.ReportRow;
import com.company.kra.common.report.ReportTable;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * XLSX through POI's streaming workbook.
 *
 * <p>SXSSF keeps only a window of rows in memory and flushes the rest to a
 * temporary file, so the size of an export is bounded by disk rather than by
 * heap. Numbers are written as numbers and dates as dates, so the recipient
 * can sort and total the sheet without cleaning it up first.</p>
 */
@Component
public class XlsxRenderer implements ReportRenderer {

    private static final int ROWS_IN_MEMORY = 200;

    @Override
    public ReportFormat format() {
        return ReportFormat.XLSX;
    }

    @Override
    public void render(ReportTable table, String heading, Map<String, String> filters,
                       OutputStream out) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROWS_IN_MEMORY)) {
            workbook.setCompressTempFiles(true);
            Sheet sheet = workbook.createSheet("Report");

            Map<ColumnType, CellStyle> styles = styles(workbook);
            CellStyle titleStyle = titleStyle(workbook);
            CellStyle headerStyle = headerStyle(workbook);

            int rowIndex = 0;
            Row titleRow = sheet.createRow(rowIndex++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(heading);
            titleCell.setCellStyle(titleStyle);

            for (Map.Entry<String, String> filter : filters.entrySet()) {
                Row filterRow = sheet.createRow(rowIndex++);
                filterRow.createCell(0).setCellValue(filter.getKey());
                filterRow.createCell(1).setCellValue(filter.getValue());
            }
            rowIndex++;

            Row header = sheet.createRow(rowIndex++);
            for (int column = 0; column < table.columns().size(); column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(table.columns().get(column).label());
                cell.setCellStyle(headerStyle);
            }

            for (ReportRow row : table.rows()) {
                Row sheetRow = sheet.createRow(rowIndex++);
                for (int column = 0; column < table.columns().size(); column++) {
                    write(sheetRow.createCell(column), row.at(column),
                            table.columns().get(column), styles);
                }
            }

            // Auto-sizing needs the rows still in memory, so the first columns
            // are tracked; a fixed width elsewhere beats a wrong one.
            for (int column = 0; column < table.columns().size(); column++) {
                sheet.setColumnWidth(column, widthFor(table.columns().get(column)));
            }

            workbook.write(out);
            workbook.dispose();
        }
    }

    private void write(Cell cell, Object value, ReportColumn column,
                       Map<ColumnType, CellStyle> styles) {
        if (value == null) {
            return;
        }
        CellStyle style = styles.get(column.type());
        if (style != null) {
            cell.setCellStyle(style);
        }

        switch (column.type()) {
            case NUMBER, PERCENT, SCORE -> {
                if (value instanceof Number number) {
                    cell.setCellValue(number.doubleValue());
                } else if (value instanceof BigDecimal decimal) {
                    cell.setCellValue(decimal.doubleValue());
                } else {
                    cell.setCellValue(String.valueOf(value));
                }
            }
            case DURATION -> cell.setCellValue(value instanceof Number minutes
                    ? minutes.doubleValue() / 60.0 : 0);
            case DATE, DATETIME -> {
                if (value instanceof LocalDate date) {
                    cell.setCellValue(date);
                } else if (value instanceof LocalDateTime moment) {
                    cell.setCellValue(moment);
                } else {
                    cell.setCellValue(String.valueOf(value));
                }
            }
            default -> cell.setCellValue(String.valueOf(value));
        }
    }

    private Map<ColumnType, CellStyle> styles(Workbook workbook) {
        Map<ColumnType, CellStyle> styles = new EnumMap<>(ColumnType.class);

        CellStyle number = workbook.createCellStyle();
        number.setDataFormat(workbook.createDataFormat().getFormat("#,##0.##"));
        number.setAlignment(HorizontalAlignment.RIGHT);
        styles.put(ColumnType.NUMBER, number);

        CellStyle percent = workbook.createCellStyle();
        percent.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00\"%\""));
        percent.setAlignment(HorizontalAlignment.RIGHT);
        styles.put(ColumnType.PERCENT, percent);

        CellStyle score = workbook.createCellStyle();
        score.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00"));
        score.setAlignment(HorizontalAlignment.RIGHT);
        styles.put(ColumnType.SCORE, score);

        CellStyle duration = workbook.createCellStyle();
        duration.setDataFormat(workbook.createDataFormat().getFormat("#,##0.0\" h\""));
        duration.setAlignment(HorizontalAlignment.RIGHT);
        styles.put(ColumnType.DURATION, duration);

        CellStyle date = workbook.createCellStyle();
        date.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
        styles.put(ColumnType.DATE, date);

        CellStyle dateTime = workbook.createCellStyle();
        dateTime.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
        styles.put(ColumnType.DATETIME, dateTime);

        return styles;
    }

    private CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private int widthFor(ReportColumn column) {
        return switch (column.type()) {
            case TEXT -> 22 * 256;
            case STATUS -> 18 * 256;
            case DATE, DATETIME -> 14 * 256;
            default -> 12 * 256;
        };
    }
}
