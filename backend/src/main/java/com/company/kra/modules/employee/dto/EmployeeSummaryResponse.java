package com.company.kra.modules.employee.dto;

import com.company.kra.common.enums.EmployeeStatus;

import java.time.LocalDate;

/**
 * Row shape for the employee directory table.
 *
 * <p>{@code status} is the employment one and {@code userStatus} the sign-in
 * one, and they are genuinely different questions: somebody employed and
 * present can still be PENDING_ACTIVATION because they have not used their
 * invite. Showing only the first is what let a new joiner read as open while
 * being unable to sign in at all.</p>
 */
public record EmployeeSummaryResponse(Long id, String employeeCode, String fullName, String email,
                                      String departmentName, String teamName, String designationName,
                                      String reportingManagerName, EmployeeStatus status,
                                      LocalDate joiningDate, String profileImageUrl, boolean active,
                                      String userStatus) {
}
