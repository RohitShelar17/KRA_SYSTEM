package com.company.kra.modules.performance.dto;

import com.company.kra.common.enums.PerformanceScope;

import java.util.List;

public record RecalculationSummaryResponse(PerformanceScope scope, String periodLabel,
                                           int processed, int skipped, int failed,
                                           List<String> messages) {
}
