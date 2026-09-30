package com.company.kra.modules.kra.dto;

import com.company.kra.common.enums.PeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** An employee's whole scorecard for one period. */
public record KraScorecardResponse(Long employeeId, String employeeName, String employeeCode,
                                   String designation, PeriodType periodType, int periodYear,
                                   int periodIndex, String periodLabel,
                                   LocalDate periodStart, LocalDate periodEnd,
                                   BigDecimal overallAchievement, boolean scored,
                                   BigDecimal weightageTotal, boolean weightageValid,
                                   int kraCount, int kpiCount, int scoredKpiCount,
                                   List<KraProgressResponse> kras) {
}
