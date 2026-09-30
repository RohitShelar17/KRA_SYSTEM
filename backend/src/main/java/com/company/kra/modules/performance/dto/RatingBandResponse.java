package com.company.kra.modules.performance.dto;

import java.math.BigDecimal;

public record RatingBandResponse(Long id, String name, String description, BigDecimal minScore,
                                 BigDecimal maxScore, String colourHex, int displayOrder) {
}
