package com.company.kra.modules.dashboard.dto;

import java.util.List;

/**
 * What the shell can honestly show in Phase 1: organisation shape and the
 * caller's place in it. Work-log, KRA and score tiles arrive with the phases
 * that produce that data, rather than being stubbed with zeros now.
 */
public record FoundationOverviewResponse(long totalEmployees, long activeEmployees, long onNoticeEmployees,
                                         long totalDepartments, long totalTeams, long totalDesignations,
                                         int directReports, int visibleEmployees, String dataScope,
                                         List<DepartmentHeadcount> headcountByDepartment) {

    public record DepartmentHeadcount(Long departmentId, String departmentName, long headcount) {
    }
}
