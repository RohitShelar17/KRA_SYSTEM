package com.company.kra.modules.organisation.dto;

public record DesignationResponse(Long id, String code, String name, String gradeLevel,
                                  Long departmentId, String departmentName, String description,
                                  boolean active) {
}
