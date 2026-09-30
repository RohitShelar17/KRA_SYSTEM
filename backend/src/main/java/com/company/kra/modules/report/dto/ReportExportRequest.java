package com.company.kra.modules.report.dto;

import com.company.kra.common.enums.ReportFormat;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record ReportExportRequest(@NotNull(message = "Choose a format") ReportFormat format,
                                  Map<String, String> filters) {
}
