package com.company.kra.modules.employee.dto;

import com.company.kra.common.enums.EmployeeStatus;
import com.company.kra.common.enums.EmploymentType;
import com.company.kra.common.enums.WorkMode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record EmployeeResponse(Long id, String employeeCode, String firstName, String lastName,
                               String fullName, String email, String phone,
                               Long departmentId, String departmentName,
                               Long teamId, String teamName,
                               Long designationId, String designationName, String gradeLevel,
                               Long reportingManagerId, String reportingManagerName,
                               LocalDate joiningDate, LocalDate exitDate,
                               EmploymentType employmentType, String workLocation, WorkMode workMode,
                               LocalTime shiftStartTime, LocalTime shiftEndTime,
                               EmployeeStatus status, String profileImageUrl, boolean active,
                               Long userId, String userStatus, Set<String> roles,
                               int directReportCount) {
}
