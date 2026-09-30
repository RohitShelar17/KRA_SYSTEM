package com.company.kra.modules.performance.dto;

import com.company.kra.common.enums.PerformanceScope;
import com.company.kra.common.enums.PerformanceStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** One score, with everything needed to explain and act on it. */
public record PerformanceSnapshotResponse(Long id, PerformanceScope scope, Long employeeId,
                                          String employeeName, String employeeCode,
                                          String periodLabel, LocalDate periodStart, LocalDate periodEnd,
                                          BigDecimal overallScore, boolean scored,
                                          String ratingBandName, String ratingBandColour,
                                          PerformanceStatus status, boolean frozen,
                                          int formulaVersion, LocalDateTime computedAt,
                                          LocalDateTime finalizedAt, String finalizedByName,
                                          BigDecimal penaltyApplied,
                                          Map<String, Object> figures,
                                          List<ScoreContributionResponse> contributions,
                                          List<AdjustmentResponse> adjustments) {
}
