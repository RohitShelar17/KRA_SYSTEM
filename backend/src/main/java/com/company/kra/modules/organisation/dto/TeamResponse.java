package com.company.kra.modules.organisation.dto;

public record TeamResponse(Long id, Long departmentId, String departmentName, String code, String name,
                           String description, Long teamLeadEmployeeId, String teamLeadName,
                           boolean active) {
}
