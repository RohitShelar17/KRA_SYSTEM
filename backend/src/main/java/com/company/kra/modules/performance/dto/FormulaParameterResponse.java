package com.company.kra.modules.performance.dto;

import com.company.kra.common.enums.PerformanceParameter;

import java.math.BigDecimal;

public record FormulaParameterResponse(Long id, PerformanceParameter parameterCode, String label,
                                       String description, BigDecimal weightage, int displayOrder) {
}
