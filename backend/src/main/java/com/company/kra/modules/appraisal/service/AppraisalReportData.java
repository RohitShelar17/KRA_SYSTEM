package com.company.kra.modules.appraisal.service;

import com.company.kra.common.report.ReportTable;
import com.company.kra.security.AccessScopeResolver;

/** Appraisal progress shaped for reporting. */
public interface AppraisalReportData {

    /** Where each appraisal stands, and what each reviewer rated. */
    ReportTable managerReview(Long companyId, AccessScopeResolver.Scope scope, Long cycleId,
                              Long departmentId, Long employeeId);

    /** How many appraisals are in flight and how many have been signed off. */
    AppraisalCounts counts(Long companyId, AccessScopeResolver.Scope scope, Long departmentId);

    record AppraisalCounts(long inFlight, long complete, long openCycles) {
    }

    /**
     * Appraisals sitting on a step longer than its configured service level.
     * The reviewer who owns the step is named where the system can name them.
     */
    java.util.List<ReviewReminder> overdueReviews(Long companyId, java.time.LocalDate today);

    record ReviewReminder(Long appraisalId, Long employeeId, String employeeName, Long reviewerId,
                          String stepName, int slaDays, long daysWaiting, String periodLabel) {
    }
}
