package com.company.kra.modules.dashboard.dto;

import com.company.kra.modules.worklog.dto.MissingSubmissionResponse;
import com.company.kra.modules.worklog.dto.WorkLogDaySummaryResponse;

import java.time.LocalDate;
import java.util.List;

/** What a manager needs before their first meeting: who filed, who did not, what is waiting. */
public record ManagerDashboardResponse(
        LocalDate date,
        int teamSize,
        int submittedForDate,
        int missingForDate,
        long pendingApprovals,
        int teamLoggedMinutes,
        int teamProductiveMinutes,
        long teamOpenTasks,
        long teamOverdueTasks,
        double submissionRatePercent,
        List<MissingSubmissionResponse> missing,
        List<WorkLogDaySummaryResponse> awaitingReview) {
}
