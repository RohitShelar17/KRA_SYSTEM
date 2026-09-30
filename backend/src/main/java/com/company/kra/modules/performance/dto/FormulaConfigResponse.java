package com.company.kra.modules.performance.dto;

import com.company.kra.common.enums.PerformanceScope;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FormulaConfigResponse(Long id, PerformanceScope scopeType, int version, String name,
                                    String notes, BigDecimal minSubmissionRatePct,
                                    BigDecimal submissionPenaltyPct, boolean active,
                                    LocalDate effectiveFrom, BigDecimal weightageTotal,
                                    List<FormulaParameterResponse> parameters) {
}
