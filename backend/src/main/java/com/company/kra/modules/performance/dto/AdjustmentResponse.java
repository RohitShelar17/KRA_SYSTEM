package com.company.kra.modules.performance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdjustmentResponse(Long id, BigDecimal previousScore, BigDecimal newScore,
                                 String reason, String adjustedByName, LocalDateTime createdAt) {
}
