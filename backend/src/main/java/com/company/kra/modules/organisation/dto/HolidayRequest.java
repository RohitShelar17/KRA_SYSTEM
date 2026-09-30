package com.company.kra.modules.organisation.dto;

import com.company.kra.common.enums.HolidayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record HolidayRequest(
        @NotNull(message = "Select a date")
        LocalDate holidayDate,

        @NotBlank(message = "Holiday name is required")
        @Size(max = 120)
        String name,

        @NotNull(message = "Select a holiday type")
        HolidayType holidayType) {
}
