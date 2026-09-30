package com.company.kra.modules.kra.dto;

import com.company.kra.common.enums.KraStatus;
import com.company.kra.common.enums.PeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record KraProgressResponse(Long id, Long employeeId, String employeeName, String name,
                                  String description, BigDecimal weightage, PeriodType periodType,
                                  int periodYear, int periodIndex, String periodLabel,
                                  LocalDate periodStart, LocalDate periodEnd, KraStatus status,
                                  BigDecimal achievementPercent, boolean scored,
                                  int scoredKpiCount, int totalKpiCount,
                                  List<KpiProgressResponse> kpis) {
}
