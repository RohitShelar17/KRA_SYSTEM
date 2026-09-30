package com.company.kra.modules.performance.service;

import com.company.kra.common.enums.PerformanceScope;
import com.company.kra.common.report.ReportTable;
import com.company.kra.security.AccessScopeResolver;

import java.math.BigDecimal;
import java.util.List;

/**
 * Score data shaped for reporting.
 *
 * <p>Every figure here comes from a stored snapshot. Nothing is recomputed at
 * report time, so a report and the employee's own screen always agree, and
 * running a report can never change a score.</p>
 */
public interface PerformanceReportData {

    /** One row per snapshot at the given grain. */
    ReportTable snapshots(Long companyId, AccessScopeResolver.Scope scope, PerformanceScope grain,
                          int year, Integer periodIndex, Long employeeId, Long departmentId,
                          Long bandId);

    /** Department averages over the monthly snapshots of a period. */
    ReportTable departmentPerformance(Long companyId, AccessScopeResolver.Scope scope, int year,
                                      Integer month);

    /** How many people landed in each rating band. */
    List<BandCount> bandDistribution(Long companyId, AccessScopeResolver.Scope scope,
                                               int year, Integer month, Long departmentId);

    /** One headline average, with how many scores it was taken over. */
    AverageScore averageScore(Long companyId, AccessScopeResolver.Scope scope, int year,
                              Integer month, Long departmentId);

    /** A rating band and the number of people in it. */
    record BandCount(Long bandId, String name, String colourHex, long count) {
    }

    /** An average and the number of scores behind it. */
    record AverageScore(BigDecimal average, long scored) {
    }

    /** Highest or lowest scorers in a period. */
    ReportTable ranking(Long companyId, AccessScopeResolver.Scope scope, int year, Integer month,
                        Long departmentId, boolean lowestFirst, int limit);
}
