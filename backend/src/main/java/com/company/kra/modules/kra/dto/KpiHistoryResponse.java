package com.company.kra.modules.kra.dto;

import com.company.kra.common.enums.ActualSource;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One period of a KPI, for the trend view on the KPI detail screen. */
public record KpiHistoryResponse(LocalDate periodStart, LocalDate periodEnd, BigDecimal targetValue,
                                 BigDecimal actualValue, BigDecimal achievementPercent,
                                 ActualSource source, String recordedByName, String note) {
}
