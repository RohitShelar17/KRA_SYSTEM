package com.company.kra.modules.appraisal.dto;

import com.company.kra.common.enums.PeriodType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CycleRequest(
        @NotBlank(message = "Give the cycle a name")
        @Size(max = 150)
        String name,

        @Size(max = 500)
        String description,

        @NotNull(message = "Select the review period type")
        PeriodType scopeType,

        @NotNull @Min(2000) @Max(2100)
        Integer periodYear,

        @NotNull @Min(1) @Max(12)
        Integer periodIndex,

        LocalDate selfAssessmentOpens,
        LocalDate selfAssessmentCloses,
        LocalDate reviewOpens,
        LocalDate reviewCloses) {
}
