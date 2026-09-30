package com.company.kra.modules.kra.dto;

import com.company.kra.common.enums.PeriodType;

import java.math.BigDecimal;
import java.util.List;

public record KraTemplateResponse(Long id, String code, String name, String description,
                                  Long designationId, String designationName,
                                  Long departmentId, String departmentName,
                                  PeriodType periodType, boolean published, boolean active,
                                  BigDecimal weightageTotal, int kraCount, int kpiCount,
                                  List<KraTemplateItemResponse> items) {
}
