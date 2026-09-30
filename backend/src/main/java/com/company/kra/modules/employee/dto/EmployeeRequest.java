package com.company.kra.modules.employee.dto;

import com.company.kra.common.enums.EmployeeStatus;
import com.company.kra.common.enums.EmploymentType;
import com.company.kra.common.enums.WorkMode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record EmployeeRequest(
        @NotBlank(message = "Employee code is required")
        @Size(max = 30)
        String employeeCode,

        @NotBlank(message = "First name is required")
        @Size(max = 80)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 80)
        String lastName,

        @NotBlank(message = "Work email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 150)
        String email,

        @Pattern(regexp = "^$|^[0-9+() -]{6,30}$", message = "Enter a valid phone number")
        String phone,

        @NotNull(message = "Select a department")
        Long departmentId,

        Long teamId,

        @NotNull(message = "Select a designation")
        Long designationId,

        Long reportingManagerId,

        @NotNull(message = "Joining date is required")
        LocalDate joiningDate,

        @NotNull(message = "Select an employment type")
        EmploymentType employmentType,

        @Size(max = 120)
        String workLocation,

        @NotNull(message = "Select a work mode")
        WorkMode workMode,

        LocalTime shiftStartTime,

        LocalTime shiftEndTime,

        EmployeeStatus status,

        /** Roles granted to the account created alongside this employee. */
        @NotEmpty(message = "Assign at least one role")
        Set<String> roleCodes) {
}
