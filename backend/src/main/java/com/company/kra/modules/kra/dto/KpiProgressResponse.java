package com.company.kra.modules.kra.dto;

import com.company.kra.common.enums.ActualSource;
import com.company.kra.common.enums.MeasurementType;
import com.company.kra.common.enums.TargetDirection;

import java.math.BigDecimal;

/**
 * A KPI with its target, actual and achievement for the period.
 *
 * <p>{@code excludedReason} is populated instead of an achievement when the
 * KPI could not be scored, so the UI can say why rather than showing a zero
 * that looks like failure.</p>
 */
public record KpiProgressResponse(Long id, String name, String description,
                                  MeasurementType measurementType, TargetDirection targetDirection,
                                  String unit, BigDecimal weightage, BigDecimal capPercent,
                                  ActualSource actualSource, boolean derived,
                                  BigDecimal targetValue, BigDecimal actualValue,
                                  BigDecimal achievementPercent, boolean scored, String excludedReason,
                                  String actualRecordedBy, String actualNote) {
}
