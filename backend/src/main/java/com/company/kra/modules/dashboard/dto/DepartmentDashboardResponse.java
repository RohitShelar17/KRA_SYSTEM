package com.company.kra.modules.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * The department head's landing page: one call, one typed payload, no
 * aggregation in the browser.
 *
 * <p>Every figure is nullable where it is genuinely unknown. An average score
 * of {@code null} means nothing has been scored yet, which is a different
 * statement from an average of zero.</p>
 */
public record DepartmentDashboardResponse(Long departmentId, String departmentName, String periodLabel,
                                          long headcount, long managers,
                                          long submittedToday, double submissionRatePercent,
                                          BigDecimal productiveRatioPercent,
                                          BigDecimal averageScore, long scoredCount,
                                          BigDecimal kraAverage, BigDecimal kpiAverage,
                                          long tasksCompleted, long tasksPending,
                                          long appraisalsInFlight, long appraisalsComplete,
                                          List<BandSlice> bandDistribution,
                                          List<TeamScore> teamComparison,
                                          List<PersonScore> topPerformers,
                                          List<PersonScore> needsSupport,
                                          List<TrendPoint> scoreTrend) {

    /** One rating band and how many people landed in it. */
    public record BandSlice(String name, String colourHex, long count) {
    }

    /** A department or team with its average, for side-by-side comparison. */
    public record TeamScore(String name, BigDecimal average, long scored) {
    }

    public record PersonScore(String employeeCode, String name, String department,
                              BigDecimal score, String band) {
    }

    public record TrendPoint(String label, BigDecimal value) {
    }
}
