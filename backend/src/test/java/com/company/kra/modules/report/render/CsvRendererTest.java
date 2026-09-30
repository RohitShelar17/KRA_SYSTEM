package com.company.kra.modules.report.render;

import com.company.kra.common.report.ReportColumn;
import com.company.kra.common.report.ReportRow;
import com.company.kra.common.report.ReportTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CSV is the format most likely to be opened in something that will not
 * tolerate a sloppy quote, so the escaping rules are worth pinning down.
 */
class CsvRendererTest {

    private final CsvRenderer renderer = new CsvRenderer();

    @Test
    @DisplayName("Headings, the filter set and the column labels are written before the rows")
    void writesHeaderBlock() throws IOException {
        String csv = render(ReportTable.of(
                List.of(ReportColumn.text("name", "Employee"), ReportColumn.score("score", "Score")),
                List.of(ReportRow.of("Priya Nair", new BigDecimal("91.25")))),
                Map.of("Department", "Software Development"));

        assertThat(csv).contains("Productivity");
        assertThat(csv).contains("Department: Software Development");
        assertThat(csv).contains("Employee,Score");
        assertThat(csv).contains("Priya Nair,91.25");
    }

    @Test
    @DisplayName("A value with a comma, a quote or a newline is quoted and escaped")
    void escapesAwkwardValues() throws IOException {
        String csv = render(ReportTable.of(
                List.of(ReportColumn.text("note", "Note")),
                List.of(ReportRow.of("Nair, Priya"),
                        ReportRow.of("He said \"fine\""),
                        ReportRow.of("line one\nline two"))),
                Map.of());

        assertThat(csv).contains("\"Nair, Priya\"");
        assertThat(csv).contains("\"He said \"\"fine\"\"\"");
        assertThat(csv).contains("\"line one\nline two\"");
    }

    @Test
    @DisplayName("A null cell is written as an empty field, not the word null")
    void nullsAreEmpty() throws IOException {
        String csv = render(ReportTable.of(
                List.of(ReportColumn.text("a", "A"), ReportColumn.text("b", "B")),
                List.of(ReportRow.of(null, "present"))),
                Map.of());

        assertThat(csv).contains(",present");
        assertThat(csv).doesNotContain("null");
    }

    @Test
    @DisplayName("The file starts with a byte-order mark so Excel reads it as UTF-8")
    void writesByteOrderMark() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        renderer.render(ReportTable.of(List.of(ReportColumn.text("a", "A")),
                List.of(ReportRow.of("Å"))), "Productivity", Map.of(), out);

        byte[] bytes = out.toByteArray();
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("Å");
    }

    @Test
    @DisplayName("Dates and decimals keep their plain form rather than scientific notation")
    void keepsPlainNumbers() throws IOException {
        String csv = render(ReportTable.of(
                List.of(ReportColumn.date("d", "Date"), ReportColumn.number("n", "Amount")),
                List.of(ReportRow.of(LocalDate.of(2026, 8, 12), new BigDecimal("0.00000015")))),
                Map.of());

        assertThat(csv).contains("2026-08-12");
        assertThat(csv).contains("0.00000015");
        assertThat(csv).doesNotContain("E-");
    }

    private String render(ReportTable table, Map<String, String> filters) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        renderer.render(table, "Productivity", filters, out);
        return out.toString(StandardCharsets.UTF_8);
    }
}
