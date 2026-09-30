package com.company.kra.modules.kra.service;

import com.company.kra.common.enums.PeriodType;
import com.company.kra.common.report.ReportTable;
import com.company.kra.security.AccessScopeResolver;

/**
 * Goal data shaped for reporting.
 *
 * <p>These read through the same scorecard the employee sees rather than
 * re-deriving achievement in report SQL. A report that computed achievement
 * its own way would eventually disagree with the screen, and the person whose
 * appraisal it feeds would be right to distrust both.</p>
 */
public interface KraReportData {

    /** One row per assigned KRA, with its weightage and achievement. */
    ReportTable kraPerformance(Long companyId, AccessScopeResolver.Scope scope, PeriodType periodType,
                               int year, int periodIndex, Long employeeId, Long departmentId);

    /** One row per KPI: target, actual, achievement, and why it was excluded if it was. */
    ReportTable kpiAchievement(Long companyId, AccessScopeResolver.Scope scope, PeriodType periodType,
                               int year, int periodIndex, Long employeeId, Long departmentId);
}
