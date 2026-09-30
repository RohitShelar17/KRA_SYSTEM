package com.company.kra.modules.organisation.dto;

public record DepartmentResponse(Long id, String code, String name, String description,
                                 Long parentDepartmentId, String parentDepartmentName,
                                 Long headEmployeeId, String headEmployeeName,
                                 long employeeCount, boolean active) {
}
