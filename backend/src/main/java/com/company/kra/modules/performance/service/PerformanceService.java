package com.company.kra.modules.performance.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.events.NotificationRaised;
import com.company.kra.common.enums.PerformanceScope;
import com.company.kra.common.enums.PerformanceStatus;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.performance.dto.AdjustScoreRequest;
import com.company.kra.modules.performance.dto.AdjustmentResponse;
import com.company.kra.modules.performance.dto.PerformanceSnapshotResponse;
import com.company.kra.modules.performance.dto.PerformanceTrendResponse;
import com.company.kra.modules.performance.dto.RecalculateRequest;
import com.company.kra.modules.performance.dto.RecalculationSummaryResponse;
import com.company.kra.modules.performance.dto.ScoreContributionResponse;
import com.company.kra.modules.performance.entity.AnnualPerformance;
import com.company.kra.modules.performance.entity.DailyPerformance;
import com.company.kra.modules.performance.entity.MonthlyPerformance;
import com.company.kra.modules.performance.entity.PerformanceAdjustment;
import com.company.kra.modules.performance.entity.PerformanceRatingBand;
import com.company.kra.modules.performance.entity.PerformanceSnapshot;
import com.company.kra.modules.performance.entity.QuarterlyPerformance;
import com.company.kra.modules.performance.repository.AnnualPerformanceRepository;
import com.company.kra.modules.performance.repository.DailyPerformanceRepository;
import com.company.kra.modules.performance.repository.MonthlyPerformanceRepository;
import com.company.kra.modules.performance.repository.PerformanceAdjustmentRepository;
import com.company.kra.modules.performance.repository.PerformanceRatingBandRepository;
import com.company.kra.modules.performance.repository.QuarterlyPerformanceRepository;
import com.company.kra.security.AccessScopeResolver;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reading scores, and the three actions that change one: recalculate, adjust
 * and freeze.
 *
 * <p>The integrity rules live here. No endpoint accepts a computed score as
 * input; an adjustment demands a reason and records what the score was before;
 * and once finalised nothing moves until someone with the right permission
 * reopens it, which is itself recorded.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final DailyPerformanceRepository dailyRepository;
    private final MonthlyPerformanceRepository monthlyRepository;
    private final QuarterlyPerformanceRepository quarterlyRepository;
    private final AnnualPerformanceRepository annualRepository;
    private final PerformanceAdjustmentRepository adjustmentRepository;
    private final PerformanceRatingBandRepository bandRepository;
    private final EmployeeRepository employeeRepository;
    private final PerformanceCalculationService calculationService;
    private final AccessScopeResolver accessScopeResolver;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    // ----------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public PerformanceSnapshotResponse daily(Long employeeId, LocalDate date) {
        AppUserPrincipal principal = requireAccess(employeeId);
        Long target = resolveEmployee(employeeId, principal);
        LocalDate perfDate = date == null ? LocalDate.now().minusDays(1) : date;

        DailyPerformance snapshot = dailyRepository.findByEmployeeIdAndPerfDate(target, perfDate)
                .orElseThrow(() -> new ResourceNotFoundException("Daily score", perfDate));
        return toResponse(snapshot, PerformanceScope.DAILY, perfDate.toString(),
                perfDate, perfDate, dailyFigures(snapshot));
    }

    @Transactional(readOnly = true)
    public PerformanceSnapshotResponse monthly(Long employeeId, Integer year, Integer month) {
        AppUserPrincipal principal = requireAccess(employeeId);
        Long target = resolveEmployee(employeeId, principal);
        LocalDate now = LocalDate.now();
        int perfYear = year == null ? now.getYear() : year;
        int perfMonth = month == null ? now.getMonthValue() : month;

        MonthlyPerformance snapshot = monthlyRepository
                .findByEmployeeIdAndPerfYearAndPerfMonth(target, (short) perfYear, (byte) perfMonth)
                .orElseThrow(() -> new ResourceNotFoundException("Monthly score",
                        perfYear + "-" + perfMonth));

        return toResponse(snapshot, PerformanceScope.MONTHLY,
                Month.of(perfMonth).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + perfYear,
                snapshot.getPeriodStart(), snapshot.getPeriodEnd(), monthlyFigures(snapshot));
    }

    @Transactional(readOnly = true)
    public PerformanceSnapshotResponse quarterly(Long employeeId, Integer year, Integer quarter) {
        AppUserPrincipal principal = requireAccess(employeeId);
        Long target = resolveEmployee(employeeId, principal);
        LocalDate now = LocalDate.now();
        int perfYear = year == null ? now.getYear() : year;
        int perfQuarter = quarter == null ? (now.getMonthValue() - 1) / 3 + 1 : quarter;

        QuarterlyPerformance snapshot = quarterlyRepository
                .findByEmployeeIdAndPerfYearAndPerfQuarter(target, (short) perfYear, (byte) perfQuarter)
                .orElseThrow(() -> new ResourceNotFoundException("Quarterly score",
                        perfYear + "-Q" + perfQuarter));

        return toResponse(snapshot, PerformanceScope.QUARTERLY, "Q" + perfQuarter + " " + perfYear,
                snapshot.getPeriodStart(), snapshot.getPeriodEnd(), Map.of(
                        "monthsIncluded", snapshot.getMonthsIncluded(),
                        "monthlyAverageScore", nullSafe(snapshot.getMonthlyAverageScore()),
                        "kraScore", nullSafe(snapshot.getKraScore()),
                        "kpiScore", nullSafe(snapshot.getKpiScore())));
    }

    @Transactional(readOnly = true)
    public PerformanceSnapshotResponse annual(Long employeeId, Integer year) {
        AppUserPrincipal principal = requireAccess(employeeId);
        Long target = resolveEmployee(employeeId, principal);
        int perfYear = year == null ? LocalDate.now().getYear() : year;

        AnnualPerformance snapshot = annualRepository
                .findByEmployeeIdAndPerfYear(target, (short) perfYear)
                .orElseThrow(() -> new ResourceNotFoundException("Annual score", perfYear));

        return toResponse(snapshot, PerformanceScope.ANNUAL, String.valueOf(perfYear),
                snapshot.getPeriodStart(), snapshot.getPeriodEnd(), Map.of(
                        "quartersIncluded", snapshot.getQuartersIncluded(),
                        "quarterlyAverageScore", nullSafe(snapshot.getQuarterlyAverageScore()),
                        "kraScore", nullSafe(snapshot.getKraScore()),
                        "kpiScore", nullSafe(snapshot.getKpiScore())));
    }

    /** The trend series behind the performance chart. */
    @Transactional(readOnly = true)
    public PerformanceTrendResponse trend(Long employeeId, PerformanceScope scope, Integer year) {
        AppUserPrincipal principal = requireAccess(employeeId);
        Long target = resolveEmployee(employeeId, principal);
        int perfYear = year == null ? LocalDate.now().getYear() : year;

        Employee employee = employeeRepository.findById(target)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", target));

        List<PerformanceTrendResponse.Point> points = new ArrayList<>();
        PerformanceScope effective = scope == null ? PerformanceScope.MONTHLY : scope;

        switch (effective) {
            case DAILY -> dailyRepository
                    .findByEmployeeIdAndPerfDateBetweenOrderByPerfDateAsc(target,
                            LocalDate.now().minusDays(29), LocalDate.now())
                    .forEach(row -> points.add(point(row.getPerfDate().toString(), row)));
            case MONTHLY -> monthlyRepository
                    .findByEmployeeIdAndPerfYearOrderByPerfMonthAsc(target, (short) perfYear)
                    .forEach(row -> points.add(point(Month.of(row.getPerfMonth())
                            .getDisplayName(TextStyle.SHORT, Locale.ENGLISH), row)));
            case QUARTERLY -> quarterlyRepository
                    .findByEmployeeIdAndPerfYearOrderByPerfQuarterAsc(target, (short) perfYear)
                    .forEach(row -> points.add(point("Q" + row.getPerfQuarter(), row)));
            case ANNUAL -> annualRepository.findByEmployeeIdOrderByPerfYearDesc(target)
                    .forEach(row -> points.add(point(String.valueOf(row.getPerfYear()), row)));
        }

        List<PerformanceTrendResponse.Point> scored = points.stream()
                .filter(p -> p.score() != null)
                .toList();
        BigDecimal latest = scored.isEmpty() ? null : scored.get(scored.size() - 1).score();
        BigDecimal previous = scored.size() < 2 ? null : scored.get(scored.size() - 2).score();
        String latestBand = scored.isEmpty() ? null : scored.get(scored.size() - 1).bandName();

        return new PerformanceTrendResponse(target, employee.fullName(), effective, latest, latestBand,
                previous, latest == null || previous == null ? null : latest.subtract(previous), points);
    }

    // --------------------------------------------------------------- change

    /**
     * Recomputes scores for a scope. Frozen snapshots are skipped rather than
     * failing the whole run, and the count of each is reported.
     *
     * <p>Deliberately not transactional: each employee is scored in the
     * calculation service's own transaction, so one skipped or failing
     * employee cannot mark a shared transaction rollback-only and take the
     * whole batch down with it.</p>
     */
    public RecalculationSummaryResponse recalculate(RecalculateRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AccessScopeResolver.Scope scope = accessScopeResolver.visibleEmployeeIds(principal);

        List<Long> targets = request.employeeIds() == null || request.employeeIds().isEmpty()
                ? defaultTargets(principal, scope)
                : request.employeeIds();

        targets.forEach(accessScopeResolver::assertCanAccess);

        LocalDate now = LocalDate.now();
        int year = request.year() == null ? now.getYear() : request.year();
        LocalDate date = request.date() == null ? now.minusDays(1) : request.date();

        int processed = 0;
        int skipped = 0;
        int failed = 0;
        List<String> messages = new ArrayList<>();

        for (Long employeeId : targets) {
            try {
                switch (request.scope()) {
                    case DAILY -> calculationService.calculateDaily(principal.getCompanyId(),
                            employeeId, date);
                    case MONTHLY -> calculationService.calculateMonthly(principal.getCompanyId(),
                            employeeId, year, request.periodIndex() == null
                                    ? now.getMonthValue() : request.periodIndex());
                    case QUARTERLY -> calculationService.calculateQuarterly(principal.getCompanyId(),
                            employeeId, year, request.periodIndex() == null
                                    ? (now.getMonthValue() - 1) / 3 + 1 : request.periodIndex());
                    case ANNUAL -> calculationService.calculateAnnual(principal.getCompanyId(),
                            employeeId, year);
                }
                processed++;
            } catch (BusinessException ex) {
                if (ex.getErrorCode() == ErrorCode.BUSINESS_RULE_VIOLATION) {
                    skipped++;
                    messages.add(ex.getMessage());
                } else {
                    failed++;
                    messages.add(ex.getMessage());
                }
            } catch (RuntimeException ex) {
                failed++;
                log.error("Recalculation failed for employee {}", employeeId, ex);
                messages.add("Employee " + employeeId + ": " + ex.getMessage());
            }
        }

        String periodLabel = periodLabel(request.scope(), year, request.periodIndex(), date);
        auditService.record(AuditModule.PERFORMANCE, AuditAction.UPDATE, "PerformanceSnapshot", null,
                null, Map.of("scope", request.scope().name(), "period", periodLabel,
                        "processed", String.valueOf(processed)), request.reason());

        log.info("Recalculated {} {} scores ({} skipped, {} failed)", processed, request.scope(),
                skipped, failed);
        return new RecalculationSummaryResponse(request.scope(), periodLabel, processed, skipped,
                failed, messages.stream().distinct().limit(10).toList());
    }

    @Transactional
    public PerformanceSnapshotResponse adjust(PerformanceScope scope, Long snapshotId,
                                              AdjustScoreRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        PerformanceSnapshot snapshot = requireSnapshot(scope, snapshotId, principal.getCompanyId());
        accessScopeResolver.assertCanAccess(snapshot.getEmployeeId());

        if (snapshot.isFrozen()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This score is finalised. Reopen it before adjusting.");
        }

        BigDecimal previous = snapshot.getOverallScore();
        snapshot.setOverallScore(request.newScore());
        snapshot.setRatingBandId(bandRepository
                .findByCompanyIdAndActiveTrueOrderByDisplayOrderAsc(principal.getCompanyId()).stream()
                .filter(band -> band.covers(request.newScore()))
                .map(PerformanceRatingBand::getId)
                .findFirst()
                .orElse(null));
        saveSnapshot(scope, snapshot);

        PerformanceAdjustment adjustment = new PerformanceAdjustment();
        adjustment.setCompanyId(principal.getCompanyId());
        adjustment.setSnapshotType(scope);
        adjustment.setSnapshotId(snapshot.getId());
        adjustment.setEmployeeId(snapshot.getEmployeeId());
        adjustment.setPreviousScore(previous);
        adjustment.setNewScore(request.newScore());
        adjustment.setReason(request.reason());
        adjustment.setAdjustedBy(principal.getEmployeeId());
        adjustment.setAdjustedByName(principal.getDisplayName());
        adjustmentRepository.save(adjustment);

        auditService.record(AuditModule.PERFORMANCE, AuditAction.ADJUST, scope.name() + "Performance",
                snapshot.getId(),
                Map.of("score", previous == null ? "none" : previous.toPlainString()),
                Map.of("score", request.newScore().toPlainString()), request.reason());

        log.info("{} score {} adjusted from {} to {} by user {}", scope, snapshot.getId(), previous,
                request.newScore(), principal.getUserId());
        return read(scope, snapshot.getId());
    }

    @Transactional
    public PerformanceSnapshotResponse finalise(PerformanceScope scope, Long snapshotId) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        PerformanceSnapshot snapshot = requireSnapshot(scope, snapshotId, principal.getCompanyId());

        if (snapshot.isFrozen()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This score is already finalised");
        }
        if (snapshot.getOverallScore() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "There is no score to finalise. Nothing could be measured for this period.");
        }

        PerformanceStatus previous = snapshot.getStatus();
        snapshot.setStatus(PerformanceStatus.FINALIZED);
        snapshot.setFinalizedAt(LocalDateTime.now());
        snapshot.setFinalizedBy(principal.getEmployeeId());
        saveSnapshot(scope, snapshot);

        auditService.record(AuditModule.PERFORMANCE, AuditAction.FINALIZE, scope.name() + "Performance",
                snapshot.getId(), Map.of("status", previous.name()),
                Map.of("status", PerformanceStatus.FINALIZED.name(),
                        "score", snapshot.getOverallScore().toPlainString()), null);

        log.info("{} score {} finalised by user {}", scope, snapshot.getId(), principal.getUserId());

        events.publishEvent(NotificationRaised
                .to(principal.getCompanyId(), "PERFORMANCE_FINALIZED")
                .recipient(snapshot.getEmployeeId())
                .about(scope.name() + "Performance", snapshot.getId())
                .with("actorName", principal.getDisplayName())
                .with("periodLabel", periodLabel(scope, snapshot))
                .with("score", snapshot.getOverallScore().toPlainString())
                .build());

        return read(scope, snapshot.getId());
    }

    @Transactional
    public PerformanceSnapshotResponse reopen(PerformanceScope scope, Long snapshotId, String reason) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        PerformanceSnapshot snapshot = requireSnapshot(scope, snapshotId, principal.getCompanyId());

        if (!snapshot.isFrozen()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This score is not finalised, so there is nothing to reopen");
        }

        snapshot.setStatus(PerformanceStatus.HR_REVIEWED);
        snapshot.setFinalizedAt(null);
        snapshot.setFinalizedBy(null);
        saveSnapshot(scope, snapshot);

        // Reopening a finalised appraisal is the most sensitive action in the
        // system, so it is audited with the reason regardless of what follows.
        auditService.record(AuditModule.PERFORMANCE, AuditAction.REOPEN, scope.name() + "Performance",
                snapshot.getId(), Map.of("status", PerformanceStatus.FINALIZED.name()),
                Map.of("status", PerformanceStatus.HR_REVIEWED.name()), reason);

        log.warn("{} score {} reopened by user {}: {}", scope, snapshot.getId(), principal.getUserId(),
                reason);

        // The employee and their manager both hear about it: a score that was
        // final and is not any more is exactly the kind of change people need
        // to be told about rather than discover.
        Employee employee = employeeRepository.findById(snapshot.getEmployeeId()).orElse(null);
        events.publishEvent(NotificationRaised
                .to(principal.getCompanyId(), "PERFORMANCE_REOPENED")
                .recipient(snapshot.getEmployeeId())
                .recipient(employee == null || employee.getReportingManager() == null
                        ? null : employee.getReportingManager().getId())
                .about(scope.name() + "Performance", snapshot.getId())
                .with("actorName", principal.getDisplayName())
                .with("employeeName", employee == null ? "an employee" : employee.fullName())
                .with("periodLabel", periodLabel(scope, snapshot))
                .with("reason", reason)
                .build());

        return read(scope, snapshot.getId());
    }

    // ------------------------------------------------------------ internals

    private PerformanceSnapshotResponse read(PerformanceScope scope, Long id) {
        return switch (scope) {
            case DAILY -> {
                DailyPerformance row = dailyRepository.findById(id).orElseThrow();
                yield toResponse(row, scope, row.getPerfDate().toString(), row.getPerfDate(),
                        row.getPerfDate(), dailyFigures(row));
            }
            case MONTHLY -> {
                MonthlyPerformance row = monthlyRepository.findById(id).orElseThrow();
                yield toResponse(row, scope, Month.of(row.getPerfMonth())
                                .getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + row.getPerfYear(),
                        row.getPeriodStart(), row.getPeriodEnd(), monthlyFigures(row));
            }
            case QUARTERLY -> {
                QuarterlyPerformance row = quarterlyRepository.findById(id).orElseThrow();
                yield toResponse(row, scope, "Q" + row.getPerfQuarter() + " " + row.getPerfYear(),
                        row.getPeriodStart(), row.getPeriodEnd(),
                        Map.of("monthsIncluded", row.getMonthsIncluded(),
                                "monthlyAverageScore", nullSafe(row.getMonthlyAverageScore())));
            }
            case ANNUAL -> {
                AnnualPerformance row = annualRepository.findById(id).orElseThrow();
                yield toResponse(row, scope, String.valueOf(row.getPerfYear()), row.getPeriodStart(),
                        row.getPeriodEnd(), Map.of("quartersIncluded", row.getQuartersIncluded(),
                                "quarterlyAverageScore", nullSafe(row.getQuarterlyAverageScore())));
            }
        };
    }

    /** The period a stored snapshot covers, worded the way the UI words it. */
    private String periodLabel(PerformanceScope scope, PerformanceSnapshot snapshot) {
        return switch (scope) {
            case DAILY -> ((DailyPerformance) snapshot).getPerfDate().toString();
            case MONTHLY -> {
                MonthlyPerformance row = (MonthlyPerformance) snapshot;
                yield Month.of(row.getPerfMonth()).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                        + " " + row.getPerfYear();
            }
            case QUARTERLY -> {
                QuarterlyPerformance row = (QuarterlyPerformance) snapshot;
                yield "Q" + row.getPerfQuarter() + " " + row.getPerfYear();
            }
            case ANNUAL -> String.valueOf(((AnnualPerformance) snapshot).getPerfYear());
        };
    }

    private PerformanceSnapshot requireSnapshot(PerformanceScope scope, Long id, Long companyId) {
        Optional<? extends PerformanceSnapshot> snapshot = switch (scope) {
            case DAILY -> dailyRepository.findByIdAndCompanyId(id, companyId);
            case MONTHLY -> monthlyRepository.findByIdAndCompanyId(id, companyId);
            case QUARTERLY -> quarterlyRepository.findByIdAndCompanyId(id, companyId);
            case ANNUAL -> annualRepository.findByIdAndCompanyId(id, companyId);
        };
        return snapshot.orElseThrow(() -> new ResourceNotFoundException(scope.name() + " score", id));
    }

    private void saveSnapshot(PerformanceScope scope, PerformanceSnapshot snapshot) {
        switch (scope) {
            case DAILY -> dailyRepository.save((DailyPerformance) snapshot);
            case MONTHLY -> monthlyRepository.save((MonthlyPerformance) snapshot);
            case QUARTERLY -> quarterlyRepository.save((QuarterlyPerformance) snapshot);
            case ANNUAL -> annualRepository.save((AnnualPerformance) snapshot);
        }
    }

    private List<Long> defaultTargets(AppUserPrincipal principal, AccessScopeResolver.Scope scope) {
        if (scope.organisationWide()) {
            return employeeRepository
                    .findByCompanyIdAndActiveTrueOrderByFirstNameAsc(principal.getCompanyId()).stream()
                    .map(Employee::getId)
                    .toList();
        }
        return List.copyOf(scope.employeeIds());
    }

    private PerformanceTrendResponse.Point point(String label, PerformanceSnapshot snapshot) {
        PerformanceRatingBand band = snapshot.getRatingBandId() == null ? null
                : bandRepository.findById(snapshot.getRatingBandId()).orElse(null);
        return new PerformanceTrendResponse.Point(label, snapshot.getOverallScore(),
                band == null ? null : band.getName(), band == null ? null : band.getColourHex(),
                snapshot.isFrozen());
    }

    private PerformanceSnapshotResponse toResponse(PerformanceSnapshot snapshot, PerformanceScope scope,
                                                   String periodLabel, LocalDate start, LocalDate end,
                                                   Map<String, Object> figures) {
        Employee employee = employeeRepository.findById(snapshot.getEmployeeId()).orElse(null);
        PerformanceRatingBand band = snapshot.getRatingBandId() == null ? null
                : bandRepository.findById(snapshot.getRatingBandId()).orElse(null);

        List<AdjustmentResponse> adjustments = adjustmentRepository
                .findBySnapshotTypeAndSnapshotIdOrderByCreatedAtDesc(scope, snapshot.getId()).stream()
                .map(row -> new AdjustmentResponse(row.getId(), row.getPreviousScore(),
                        row.getNewScore(), row.getReason(), row.getAdjustedByName(), row.getCreatedAt()))
                .toList();

        String finalisedBy = snapshot.getFinalizedBy() == null ? null
                : employeeRepository.findById(snapshot.getFinalizedBy())
                .map(Employee::fullName).orElse(null);

        Breakdown breakdown = parseBreakdown(snapshot.getScoreBreakdown());

        return new PerformanceSnapshotResponse(snapshot.getId(), scope, snapshot.getEmployeeId(),
                employee == null ? null : employee.fullName(),
                employee == null ? null : employee.getEmployeeCode(),
                periodLabel, start, end, snapshot.getOverallScore(), snapshot.getOverallScore() != null,
                band == null ? null : band.getName(), band == null ? null : band.getColourHex(),
                snapshot.getStatus(), snapshot.isFrozen(), snapshot.getFormulaVersion(),
                snapshot.getComputedAt(), snapshot.getFinalizedAt(), finalisedBy,
                breakdown.penalty(), figures, breakdown.contributions(), adjustments);
    }

    private record Breakdown(BigDecimal penalty, List<ScoreContributionResponse> contributions) {
    }

    /** The stored breakdown is read back as-is; it is history, not something to recompute. */
    private Breakdown parseBreakdown(String json) {
        if (json == null || json.isBlank()) {
            return new Breakdown(null, List.of());
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            List<ScoreContributionResponse> contributions = new ArrayList<>();
            for (JsonNode node : root.path("parameters")) {
                contributions.add(new ScoreContributionResponse(
                        node.path("parameterCode").asText(),
                        node.path("label").asText(),
                        node.hasNonNull("rawValue") ? node.get("rawValue").decimalValue() : null,
                        node.path("weightage").decimalValue(),
                        node.path("effectiveWeightage").decimalValue(),
                        node.path("contribution").decimalValue(),
                        node.path("measured").asBoolean(),
                        node.hasNonNull("skippedReason") ? node.get("skippedReason").asText() : null));
            }
            BigDecimal penalty = root.hasNonNull("penaltyApplied")
                    ? root.get("penaltyApplied").decimalValue() : null;
            return new Breakdown(penalty == null || penalty.signum() == 0 ? null : penalty, contributions);
        } catch (Exception ex) {
            log.warn("Could not read a stored score breakdown", ex);
            return new Breakdown(null, List.of());
        }
    }

    private Map<String, Object> dailyFigures(DailyPerformance row) {
        Map<String, Object> figures = new LinkedHashMap<>();
        figures.put("tasksAssigned", row.getTasksAssigned());
        figures.put("tasksCompleted", row.getTasksCompleted());
        figures.put("loggedMinutes", row.getLoggedMinutes());
        figures.put("productiveMinutes", row.getProductiveMinutes());
        figures.put("expectedMinutes", row.getExpectedMinutes());
        return figures;
    }

    private Map<String, Object> monthlyFigures(MonthlyPerformance row) {
        Map<String, Object> figures = new LinkedHashMap<>();
        figures.put("tasksAssigned", row.getTasksAssigned());
        figures.put("tasksCompleted", row.getTasksCompleted());
        figures.put("tasksPending", row.getTasksPending());
        figures.put("loggedMinutes", row.getLoggedMinutes());
        figures.put("productiveMinutes", row.getProductiveMinutes());
        figures.put("expectedMinutes", row.getExpectedMinutes());
        figures.put("workingDays", row.getWorkingDays());
        figures.put("submittedDays", row.getSubmittedDays());
        figures.put("submissionRatePct", nullSafe(row.getSubmissionRatePct()));
        return figures;
    }

    private Object nullSafe(BigDecimal value) {
        return value == null ? "" : value;
    }

    private String periodLabel(PerformanceScope scope, int year, Integer index, LocalDate date) {
        return switch (scope) {
            case DAILY -> date.toString();
            case MONTHLY -> Month.of(index == null ? LocalDate.now().getMonthValue() : index)
                    .getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
            case QUARTERLY -> "Q" + (index == null ? (LocalDate.now().getMonthValue() - 1) / 3 + 1 : index)
                    + " " + year;
            case ANNUAL -> String.valueOf(year);
        };
    }

    private AppUserPrincipal requireAccess(Long employeeId) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        if (employeeId != null && !employeeId.equals(principal.getEmployeeId())) {
            accessScopeResolver.assertCanAccess(employeeId);
        }
        return principal;
    }

    private Long resolveEmployee(Long employeeId, AppUserPrincipal principal) {
        if (employeeId != null) {
            return employeeId;
        }
        if (principal.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "This account is not linked to an employee record");
        }
        return principal.getEmployeeId();
    }
}
