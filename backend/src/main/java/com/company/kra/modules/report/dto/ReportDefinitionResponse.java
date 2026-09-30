package com.company.kra.modules.report.dto;

import com.company.kra.common.enums.ReportFilterKey;

import java.util.Set;

/** One entry in the report catalogue. */
public record ReportDefinitionResponse(String key, String title, String description, String group,
                                       Set<ReportFilterKey> filters) {
}
