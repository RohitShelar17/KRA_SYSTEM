package com.company.kra.modules.kra.dto;

import java.math.BigDecimal;
import java.util.List;

public record KraTemplateItemResponse(Long id, String name, String description, BigDecimal weightage,
                                      int displayOrder, BigDecimal kpiWeightageTotal,
                                      List<KpiTemplateItemResponse> kpis) {
}
