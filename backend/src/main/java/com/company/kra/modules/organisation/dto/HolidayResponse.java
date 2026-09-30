package com.company.kra.modules.organisation.dto;

import com.company.kra.common.enums.HolidayType;

import java.time.LocalDate;

public record HolidayResponse(Long id, LocalDate holidayDate, String name, HolidayType holidayType,
                              String dayOfWeek) {
}
