package com.company.kra.modules.appraisal.service;

import com.company.kra.common.enums.CycleStatus;
import com.company.kra.common.report.ReportColumn;
import com.company.kra.common.report.ReportRow;
import com.company.kra.common.report.ReportTable;
import com.company.kra.modules.appraisal.repository.AppraisalRecordRepository;
import com.company.kra.modules.appraisal.repository.PerformanceCycleRepository;
import com.company.kra.security.AccessScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.kra.common.enums.AppraisalStep;
import com.company.kra.common.enums.PeriodType;
import com.company.kra.common.enums.WorkflowStepType;
import com.company.kra.modules.appraisal.entity.ReviewWorkflowStep;
import com.company.kra.modules.appraisal.repository.ReviewWorkflowStepRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AppraisalReportDataProvider implements AppraisalReportData {

    private static final Set<Long> NO_IDS = Set.of(-1L);

    private final AppraisalRecordRepository appraisalRepository;
    private final PerformanceCycleRepository cycleRepository;
    private final ReviewWorkflowStepRepository stepRepository;

    @Override
    @Transactional(readOnly = true)
    public ReportTable managerReview(Long companyId, AccessScopeResolver.Scope scope, Long cycleId,
                                     Long departmentId, Long employeeId) {
        List<ReportColumn> columns = List.of(
                ReportColumn.text("employeeCode", "Code"),
                ReportColumn.text("employeeName", "Employee"),
                ReportColumn.text("department", "Department"),
                ReportColumn.text("manager", "Reviewer"),
                ReportColumn.text("cycle", "Cycle"),
                ReportColumn.status("currentStep", "Waiting on"),
                ReportColumn.status("status", "Status"),
                ReportColumn.text("selfAssessment", "Self assessment"),
                ReportColumn.number("selfRating", "Self rating"),
                ReportColumn.number("managerRating", "Manager rating"),
                ReportColumn.number("qualityRating", "Quality rating"),
                ReportColumn.number("hrRating", "HR rating"),
                ReportColumn.score("computedScore", "Measured score"),
                ReportColumn.score("finalScore", "Final score"),
                ReportColumn.date("completedOn", "Completed"));

        List<ReportRow> rows = appraisalRepository
                .reportAppraisals(companyId, scope.organisationWide(),
                        scope.organisationWide() || scope.employeeIds().isEmpty()
                                ? NO_IDS : scope.employeeIds(),
                        cycleId, departmentId, employeeId)
                .stream()
                .map(row -> {
                    LocalDateTime completedAt = (LocalDateTime) row[14];
                    return ReportRow.of(row[0], row[1], row[2], row[3], row[4], row[5], row[6],
                            // A missing row means the employee never started it,
                            // which reads differently from a saved draft.
                            row[7] == null ? "Not started" : row[7], row[8], row[9], row[10],
                            row[11], row[12], row[13],
                            completedAt == null ? null : completedAt.toLocalDate());
                })
                .toList();

        return ReportTable.of(columns, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewReminder> overdueReviews(Long companyId, LocalDate today) {
        Map<WorkflowStepType, ReviewWorkflowStep> steps = new HashMap<>();
        stepRepository.findByCompanyIdAndActiveTrueOrderByStepOrderAsc(companyId)
                .forEach(step -> steps.put(step.getStepType(), step));

        List<ReviewReminder> overdue = new ArrayList<>();
        for (Object[] row : appraisalRepository.findInFlight(companyId)) {
            AppraisalStep current = (AppraisalStep) row[4];
            WorkflowStepType type = current.asStepType();
            ReviewWorkflowStep configured = type == null ? null : steps.get(type);
            if (configured == null || configured.getSlaDays() == null) {
                // No service level configured for this step means nothing to be
                // late against; silence beats inventing a deadline.
                continue;
            }

            LocalDateTime since = (LocalDateTime) row[5];
            long waiting = ChronoUnit.DAYS.between(since.toLocalDate(), today);
            if (waiting <= configured.getSlaDays()) {
                continue;
            }

            Long reviewerId = current == AppraisalStep.MANAGER_REVIEW
                    ? (row[3] == null ? null : ((Number) row[3]).longValue())
                    : current == AppraisalStep.SELF_ASSESSMENT
                            ? ((Number) row[1]).longValue()
                            : null;

            overdue.add(new ReviewReminder(((Number) row[0]).longValue(),
                    ((Number) row[1]).longValue(), (String) row[2], reviewerId,
                    configured.getName(), configured.getSlaDays(), waiting,
                    periodLabel((PeriodType) row[9], (Number) row[7], (Number) row[8])));
        }
        return overdue;
    }

    private String periodLabel(PeriodType type, Number year, Number index) {
        return type == null || year == null ? "the current period"
                : type.label(year.intValue(), index == null ? 1 : index.intValue());
    }

    @Override
    @Transactional(readOnly = true)
    public AppraisalCounts counts(Long companyId, AccessScopeResolver.Scope scope,
                                  Long departmentId) {
        List<Object[]> rows = appraisalRepository.reportCounts(companyId, scope.organisationWide(),
                scope.organisationWide() || scope.employeeIds().isEmpty()
                        ? NO_IDS : scope.employeeIds(),
                departmentId);

        long openCycles = cycleRepository
                .findByCompanyIdAndStatusInAndActiveTrueOrderByPeriodStartDesc(companyId,
                        List.of(CycleStatus.OPEN, CycleStatus.IN_REVIEW))
                .size();

        if (rows.isEmpty() || rows.get(0)[0] == null) {
            return new AppraisalCounts(0, 0, openCycles);
        }
        Object[] row = rows.get(0);
        return new AppraisalCounts(count(row[0]), count(row[1]), openCycles);
    }

    private long count(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
