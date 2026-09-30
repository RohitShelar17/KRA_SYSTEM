package com.company.kra.modules.appraisal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Saved as a draft as often as the employee likes; submitting is a separate,
 * one-way action.
 */
public record SelfAssessmentRequest(
        @Size(max = 4000) String achievements,
        @Size(max = 4000) String challenges,
        @Size(max = 4000) String skillsDeveloped,
        @Size(max = 4000) String improvementAreas,
        @Size(max = 4000) String trainingNeeded,
        @Size(max = 4000) String futureGoals,
        @Size(max = 4000) String comments,

        @DecimalMin(value = "1.0", message = "Rate yourself between 1 and 5")
        @DecimalMax(value = "5.0", message = "Rate yourself between 1 and 5")
        BigDecimal selfRating) {
}
