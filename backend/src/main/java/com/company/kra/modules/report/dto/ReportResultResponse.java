package com.company.kra.modules.report.dto;

import com.company.kra.common.report.ReportColumn;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A page of a report's result.
 *
 * <p>{@code truncated} is honest about a capped result: a report that quietly
 * stopped at ten thousand rows would be read as the whole picture.</p>
 */
public record ReportResultResponse(String key, String title, String description,
                                   List<ReportColumn> columns, List<List<Object>> rows,
                                   int page, int size, long totalRows, int totalPages,
                                   boolean truncated, LocalDateTime generatedAt) {
}
