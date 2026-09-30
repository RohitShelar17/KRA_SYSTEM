package com.company.kra.modules.kra.dto;

import com.company.kra.common.enums.PeriodType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AssignKraRequest(
        @NotNull(message = "Select a template")
        Long templateId,

        @NotNull(message = "Select a period type")
        PeriodType periodType,

        @NotNull(message = "Enter the year")
        @Min(2000) @Max(2100)
        Integer periodYear,

        /** 1-12 for monthly, 1-4 for quarterly, 1 for annual. */
        @NotNull(message = "Select the period")
        @Min(1) @Max(53)
        Integer periodIndex,

        /** Copy the template's default target values across as starting targets. */
        boolean seedTargets,

        /** Replace an existing assignment for the same period instead of refusing. */
        boolean replaceExisting) {
}
