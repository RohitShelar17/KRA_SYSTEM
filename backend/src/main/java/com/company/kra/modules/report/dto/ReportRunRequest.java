package com.company.kra.modules.report.dto;

import java.util.Map;

/**
 * A request to run a report.
 *
 * <p>Filters arrive as names rather than positions so adding a filter to a
 * report does not change the shape of every caller.</p>
 */
public record ReportRunRequest(Map<String, String> filters, Integer page, Integer size) {
}
