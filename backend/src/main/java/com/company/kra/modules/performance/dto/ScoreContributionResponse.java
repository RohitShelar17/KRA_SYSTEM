package com.company.kra.modules.performance.dto;

import java.math.BigDecimal;

/**
 * One line of the explanation behind a score: what was measured, the weight it
 * carried after redistribution, and what it actually added.
 */
public record ScoreContributionResponse(String parameterCode, String label, BigDecimal rawValue,
                                        BigDecimal weightage, BigDecimal effectiveWeightage,
                                        BigDecimal contribution, boolean measured,
                                        String skippedReason) {
}
