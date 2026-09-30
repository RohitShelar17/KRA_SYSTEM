package com.company.kra.modules.kra.dto;

import com.company.kra.common.enums.ActualSource;
import com.company.kra.common.enums.MeasurementType;
import com.company.kra.common.enums.TargetDirection;

import java.math.BigDecimal;

public record KpiTemplateItemResponse(Long id, String name, String description,
                                      MeasurementType measurementType, TargetDirection targetDirection,
                                      String unit, BigDecimal defaultTargetValue, BigDecimal weightage,
                                      BigDecimal capPercent, ActualSource actualSource, int displayOrder) {
}
