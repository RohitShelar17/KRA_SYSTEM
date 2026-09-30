package com.company.kra.modules.dashboard.dto;

import com.company.kra.modules.dashboard.dto.DepartmentDashboardResponse.BandSlice;
import com.company.kra.modules.dashboard.dto.DepartmentDashboardResponse.PersonScore;
import com.company.kra.modules.dashboard.dto.DepartmentDashboardResponse.TeamScore;
import com.company.kra.modules.dashboard.dto.DepartmentDashboardResponse.TrendPoint;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The organisation-wide view.
 *
 * <p>It carries two things a domain dashboard usually leaves out: how the
 * scheduled scoring jobs have been running, and the last few audited actions.
 * A scheduler that stopped and a permission that was quietly changed are both
 * invisible until somebody is shown them.</p>
 */
public record AdminDashboardResponse(String periodLabel,
                                     long totalEmployees, long activeEmployees,
                                     long departments, long teams, long designations,
                                     long submittedToday, double submissionRatePercent,
                                     BigDecimal productiveRatioPercent,
                                     BigDecimal averageScore, long scoredCount,
                                     long openCycles, long appraisalsComplete,
                                     List<BandSlice> bandDistribution,
                                     List<TeamScore> departmentComparison,
                                     List<PersonScore> topPerformers,
                                     List<PersonScore> needsSupport,
                                     List<TrendPoint> submissionTrend,
                                     List<JobRunSummary> scheduledJobs,
                                     List<AuditEntrySummary> recentActivity) {

    public record JobRunSummary(String jobName, String period, String status, int processed,
                                int failed, String message, LocalDateTime startedAt,
                                LocalDateTime finishedAt, boolean healthy) {
    }

    public record AuditEntrySummary(String actorName, String module, String action,
                                    String entityType, Long entityId, String reason,
                                    LocalDateTime at) {
    }
}
