package com.company.kra.modules.appraisal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Competencies are rated 1 to 5; the ratings the engine reads are validated on submit. */
public record ManagerReviewRequest(
        @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal qualityRating,
        @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal productivityRating,
        @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal timelinessRating,
        @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal teamworkRating,
        @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal communicationRating,
        @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal initiativeRating,
        @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal problemSolvingRating,
        @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal disciplineRating,
        @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal kraRating,
        @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal kpiRating,
        @DecimalMin("1.0") @DecimalMax("5.0") BigDecimal overallRating,

        @Size(max = 4000) String strengths,
        @Size(max = 4000) String improvements,
        @Size(max = 4000) String comments) {
}
