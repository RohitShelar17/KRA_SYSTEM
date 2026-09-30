package com.company.kra.modules.employee.dto;

import com.company.kra.common.enums.EmployeeStatus;
import com.company.kra.common.enums.EmploymentType;
import com.company.kra.common.enums.WorkMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Employee code and work email are identity, not profile: they are set at
 * creation and changed only through a dedicated, audited action.
 */
public record EmployeeUpdateRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 80)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 80)
        String lastName,

        @Pattern(regexp = "^$|^[0-9+() -]{6,30}$", message = "Enter a valid phone number")
        String phone,

        @NotNull(message = "Select a department")
        Long departmentId,

        Long teamId,

        @NotNull(message = "Select a designation")
        Long designationId,

        @NotNull(message = "Joining date is required")
        LocalDate joiningDate,

        LocalDate exitDate,

        @NotNull(message = "Select an employment type")
        EmploymentType employmentType,

        @Size(max = 120)
        String workLocation,

        @NotNull(message = "Select a work mode")
        WorkMode workMode,

        LocalTime shiftStartTime,

        LocalTime shiftEndTime,

        @NotNull(message = "Select a status")
        EmployeeStatus status,

        Set<String> roleCodes) {
}
