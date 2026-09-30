package com.company.kra.modules.employee.dto;

import com.company.kra.common.enums.RelationType;

import java.time.LocalDate;

/** One historical row of the effective-dated reporting hierarchy. */
public record ReportingLineResponse(Long id, Long employeeId, Long managerId, String managerName,
                                    RelationType relationType, LocalDate validFrom, LocalDate validTo,
                                    boolean current) {
}
