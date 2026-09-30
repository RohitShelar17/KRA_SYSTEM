package com.company.kra.modules.performance.service;

import com.company.kra.common.enums.PerformanceParameter;
import com.company.kra.common.events.NotificationRaised;
import com.company.kra.common.enums.PerformanceScope;
import com.company.kra.common.enums.PerformanceStatus;
import com.company.kra.common.metrics.KraMetrics;
import com.company.kra.common.metrics.ReviewMetrics;
import com.company.kra.common.metrics.TaskMetrics;
import com.company.kra.common.metrics.TimesheetMetrics;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.modules.performance.entity.AnnualPerformance;
import com.company.kra.modules.performance.entity.DailyPerformance;
import com.company.kra.modules.performance.entity.MonthlyPerformance;
import com.company.kra.modules.performance.entity.PerformanceSnapshot;
import com.company.kra.modules.performance.entity.PerformanceFormulaConfig;
import com.company.kra.modules.performance.entity.QuarterlyPerformance;
import com.company.kra.modules.performance.repository.AnnualPerformanceRepository;
import com.company.kra.modules.performance.repository.DailyPerformanceRepository;
import com.company.kra.modules.performance.repository.MonthlyPerformanceRepository;
import com.company.kra.modules.performance.repository.QuarterlyPerformanceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The scoring engine. Everything that turns recorded work into a score happens
 * here and nowhere else.
 *
 * <p>Three rules hold at every grain. Scores are computed once and stored,
 * with the formula version and a breakdown of every parameter's contribution,
 * so a figure can be explained months later. A parameter that cannot be
 * measured is excluded and its weight redistributed, never scored as zero. And
 * a FINALIZED snapshot is refused outright — the freeze is real.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceCalculationService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final DailyPerformanceRepository dailyRepository;
    private final MonthlyPerformanceRepository monthlyRepository;
    private final QuarterlyPerformanceRepository quarterlyRepository;
    private final AnnualPerformanceRepository annualRepository;
    private final FormulaConfigService formulaService;
    private final RatingBandService bandService;
    private final ScoreComposer composer;
    private final TimesheetMetrics timesheetMetrics;
    private final TaskMetrics taskMetrics;
    private final KraMetrics kraMetrics;
    private final ReviewMetrics reviewMetrics;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    // ---------------------------------------------------------------- daily

    @Transactional
    public DailyPerformance calculateDaily(Long companyId, Long employeeId, LocalDate date) {
        DailyPerformance existing = dailyRepository.findByEmployeeIdAndPerfDate(employeeId, date)
                .orElse(null);
        assertNotFrozen(existing == null ? null : existing.getStatus(), "daily", date.toString());

        PerformanceFormulaConfig config = formulaService.activeConfig(companyId, PerformanceScope.DAILY);

        TimesheetMetrics.TimeSummary time = timesheetMetrics.summarise(employeeId, date, date);
        TaskMetrics.TaskSummary tasks = taskMetrics.summarise(employeeId, date, date);
        int expectedMinutes = timesheetMetrics.expectedMinutesPerDay(employeeId);

        // A week-off or holiday with nothing logged is not a zero-productivity
        // day, it is a day that does not count. Skipping it keeps weekends out
        // of the monthly average. Someone who did work and filed it still gets
        // scored, whatever the calendar says.
        boolean workingDay = timesheetMetrics.workingDaysBetween(companyId, date, date) > 0;
        if (!workingDay && time.loggedMinutes() == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    date + " is not a working day and nothing was logged, so it is not scored");
        }

        Map<PerformanceParameter, BigDecimal> inputs = new EnumMap<>(PerformanceParameter.class);
        inputs.put(PerformanceParameter.TASK_COMPLETION,
                taskMetrics.completionRatePercent(employeeId, date, date));
        inputs.put(PerformanceParameter.KRA_ACHIEVEMENT,
                kraMetrics.kraAchievementPercent(employeeId, date, date));
        inputs.put(PerformanceParameter.KPI_ACHIEVEMENT,
                kraMetrics.kpiAchievementPercent(employeeId, date, date));
        inputs.put(PerformanceParameter.PRODUCTIVITY,
                productivityPercent(time.productiveMinutes(), expectedMinutes));
        inputs.put(PerformanceParameter.QUALITY_RATING,
                reviewMetrics.qualityRatingPercent(employeeId, date, date));
        inputs.put(PerformanceParameter.MANAGER_RATING,
                reviewMetrics.managerRatingPercent(employeeId, date, date));
        inputs.values().removeIf(java.util.Objects::isNull);

        ScoreComposer.Result result = composer.compose(config, inputs);

        DailyPerformance snapshot = existing == null ? new DailyPerformance() : existing;
        snapshot.setCompanyId(companyId);
        snapshot.setEmployeeId(employeeId);
        snapshot.setPerfDate(date);
        snapshot.setTasksAssigned((int) tasks.assigned());
        snapshot.setTasksCompleted((int) tasks.completed());
        snapshot.setLoggedMinutes(time.loggedMinutes());
        snapshot.setProductiveMinutes(time.productiveMinutes());
        snapshot.setExpectedMinutes(expectedMinutes);
        snapshot.setTaskCompletionScore(inputs.get(PerformanceParameter.TASK_COMPLETION));
        snapshot.setKraScore(inputs.get(PerformanceParameter.KRA_ACHIEVEMENT));
        snapshot.setProductivityScore(inputs.get(PerformanceParameter.PRODUCTIVITY));
        snapshot.setQualityScore(inputs.get(PerformanceParameter.QUALITY_RATING));
        snapshot.setManagerRating(inputs.get(PerformanceParameter.MANAGER_RATING));
        applyResult(snapshot, result, companyId);

        return dailyRepository.save(snapshot);
    }

    // -------------------------------------------------------------- monthly

    @Transactional
    public MonthlyPerformance calculateMonthly(Long companyId, Long employeeId, int year, int month) {
        MonthlyPerformance existing = monthlyRepository
                .findByEmployeeIdAndPerfYearAndPerfMonth(employeeId, (short) year, (byte) month)
                .orElse(null);
        assertNotFrozen(existing == null ? null : existing.getStatus(), "monthly", year + "-" + month);

        PerformanceFormulaConfig config = formulaService.activeConfig(companyId, PerformanceScope.MONTHLY);
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        TimesheetMetrics.TimeSummary time = timesheetMetrics.summarise(employeeId, start, end);
        TaskMetrics.TaskSummary tasks = taskMetrics.summarise(employeeId, start, end);
        int workingDays = timesheetMetrics.workingDaysBetween(companyId, start, end);
        int expectedMinutes = workingDays * timesheetMetrics.expectedMinutesPerDay(employeeId);

        BigDecimal submissionRate = workingDays == 0 ? null
                : BigDecimal.valueOf(time.submittedDays())
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(workingDays), 2, RoundingMode.HALF_UP);

        Map<PerformanceParameter, BigDecimal> inputs = new EnumMap<>(PerformanceParameter.class);
        inputs.put(PerformanceParameter.DAILY_AVERAGE, dailyRepository.averageScore(employeeId, start, end));
        inputs.put(PerformanceParameter.TASK_COMPLETION,
                taskMetrics.completionRatePercent(employeeId, start, end));
        inputs.put(PerformanceParameter.KRA_ACHIEVEMENT,
                kraMetrics.kraAchievementPercent(employeeId, start, end));
        inputs.put(PerformanceParameter.KPI_ACHIEVEMENT,
                kraMetrics.kpiAchievementPercent(employeeId, start, end));
        inputs.put(PerformanceParameter.PRODUCTIVITY,
                productivityPercent(time.productiveMinutes(), expectedMinutes));
        inputs.put(PerformanceParameter.QUALITY_RATING,
                reviewMetrics.qualityRatingPercent(employeeId, start, end));
        inputs.put(PerformanceParameter.MANAGER_RATING,
                reviewMetrics.managerRatingPercent(employeeId, start, end));
        inputs.values().removeIf(java.util.Objects::isNull);

        ScoreComposer.Result result = composer.compose(config, inputs, submissionRate);

        MonthlyPerformance snapshot = existing == null ? new MonthlyPerformance() : existing;
        snapshot.setCompanyId(companyId);
        snapshot.setEmployeeId(employeeId);
        snapshot.setPerfYear((short) year);
        snapshot.setPerfMonth((byte) month);
        snapshot.setPeriodStart(start);
        snapshot.setPeriodEnd(end);
        snapshot.setTasksAssigned((int) tasks.assigned());
        snapshot.setTasksCompleted((int) tasks.completed());
        snapshot.setTasksPending((int) Math.max(tasks.assigned() - tasks.completed(), 0));
        snapshot.setLoggedMinutes(time.loggedMinutes());
        snapshot.setProductiveMinutes(time.productiveMinutes());
        snapshot.setExpectedMinutes(expectedMinutes);
        snapshot.setWorkingDays(workingDays);
        snapshot.setSubmittedDays(time.submittedDays());
        snapshot.setSubmissionRatePct(submissionRate);
        snapshot.setDailyAverageScore(inputs.get(PerformanceParameter.DAILY_AVERAGE));
        snapshot.setTaskCompletionScore(inputs.get(PerformanceParameter.TASK_COMPLETION));
        snapshot.setKraScore(inputs.get(PerformanceParameter.KRA_ACHIEVEMENT));
        snapshot.setKpiScore(inputs.get(PerformanceParameter.KPI_ACHIEVEMENT));
        snapshot.setProductivityScore(inputs.get(PerformanceParameter.PRODUCTIVITY));
        snapshot.setQualityScore(inputs.get(PerformanceParameter.QUALITY_RATING));
        snapshot.setManagerRating(inputs.get(PerformanceParameter.MANAGER_RATING));
        applyResult(snapshot, result, companyId);

        MonthlyPerformance saved = monthlyRepository.save(snapshot);
        announce(companyId, employeeId, "monthly", monthLabel(year, month), saved);
        return saved;
    }

    // ------------------------------------------------------------ quarterly

    @Transactional
    public QuarterlyPerformance calculateQuarterly(Long companyId, Long employeeId, int year, int quarter) {
        QuarterlyPerformance existing = quarterlyRepository
                .findByEmployeeIdAndPerfYearAndPerfQuarter(employeeId, (short) year, (byte) quarter)
                .orElse(null);
        assertNotFrozen(existing == null ? null : existing.getStatus(), "quarterly", year + "-Q" + quarter);

        PerformanceFormulaConfig config = formulaService.activeConfig(companyId, PerformanceScope.QUARTERLY);
        int firstMonth = (quarter - 1) * 3 + 1;
        LocalDate start = LocalDate.of(year, firstMonth, 1);
        LocalDate end = YearMonth.of(year, firstMonth + 2).atEndOfMonth();

        List<MonthlyPerformance> months = monthlyRepository.findInMonthRange(employeeId, (short) year,
                (byte) firstMonth, (byte) (firstMonth + 2));
        BigDecimal monthlyAverage = average(months.stream()
                .map(MonthlyPerformance::getOverallScore)
                .filter(java.util.Objects::nonNull)
                .toList());

        Map<PerformanceParameter, BigDecimal> inputs = new EnumMap<>(PerformanceParameter.class);
        inputs.put(PerformanceParameter.MONTHLY_AVERAGE, monthlyAverage);
        inputs.put(PerformanceParameter.KRA_ACHIEVEMENT,
                kraMetrics.kraAchievementPercent(employeeId, start, end));
        inputs.put(PerformanceParameter.KPI_ACHIEVEMENT,
                kraMetrics.kpiAchievementPercent(employeeId, start, end));
        inputs.put(PerformanceParameter.MANAGER_RATING,
                reviewMetrics.managerRatingPercent(employeeId, start, end));
        inputs.put(PerformanceParameter.DEPARTMENT_HEAD_RATING,
                reviewMetrics.departmentHeadRatingPercent(employeeId, start, end));
        inputs.values().removeIf(java.util.Objects::isNull);

        ScoreComposer.Result result = composer.compose(config, inputs);

        QuarterlyPerformance snapshot = existing == null ? new QuarterlyPerformance() : existing;
        snapshot.setCompanyId(companyId);
        snapshot.setEmployeeId(employeeId);
        snapshot.setPerfYear((short) year);
        snapshot.setPerfQuarter((byte) quarter);
        snapshot.setPeriodStart(start);
        snapshot.setPeriodEnd(end);
        snapshot.setMonthsIncluded(months.size());
        snapshot.setMonthlyAverageScore(monthlyAverage);
        snapshot.setKraScore(inputs.get(PerformanceParameter.KRA_ACHIEVEMENT));
        snapshot.setKpiScore(inputs.get(PerformanceParameter.KPI_ACHIEVEMENT));
        snapshot.setManagerRating(inputs.get(PerformanceParameter.MANAGER_RATING));
        applyResult(snapshot, result, companyId);

        QuarterlyPerformance saved = quarterlyRepository.save(snapshot);
        announce(companyId, employeeId, "quarterly", "Q" + quarter + " " + year, saved);
        return saved;
    }

    // --------------------------------------------------------------- annual

    @Transactional
    public AnnualPerformance calculateAnnual(Long companyId, Long employeeId, int year) {
        AnnualPerformance existing = annualRepository.findByEmployeeIdAndPerfYear(employeeId, (short) year)
                .orElse(null);
        assertNotFrozen(existing == null ? null : existing.getStatus(), "annual", String.valueOf(year));

        PerformanceFormulaConfig config = formulaService.activeConfig(companyId, PerformanceScope.ANNUAL);
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        List<QuarterlyPerformance> quarters =
                quarterlyRepository.findByEmployeeIdAndPerfYearOrderByPerfQuarterAsc(employeeId, (short) year);
        BigDecimal quarterlyAverage = average(quarters.stream()
                .map(QuarterlyPerformance::getOverallScore)
                .filter(java.util.Objects::nonNull)
                .toList());

        Map<PerformanceParameter, BigDecimal> inputs = new EnumMap<>(PerformanceParameter.class);
        inputs.put(PerformanceParameter.QUARTERLY_AVERAGE, quarterlyAverage);
        inputs.put(PerformanceParameter.KRA_ACHIEVEMENT,
                kraMetrics.kraAchievementPercent(employeeId, start, end));
        inputs.put(PerformanceParameter.KPI_ACHIEVEMENT,
                kraMetrics.kpiAchievementPercent(employeeId, start, end));
        inputs.put(PerformanceParameter.MANAGER_RATING,
                reviewMetrics.managerRatingPercent(employeeId, start, end));
        inputs.put(PerformanceParameter.DEPARTMENT_HEAD_RATING,
                reviewMetrics.departmentHeadRatingPercent(employeeId, start, end));
        inputs.put(PerformanceParameter.SELF_ASSESSMENT,
                reviewMetrics.selfRatingPercent(employeeId, start, end));
        inputs.values().removeIf(java.util.Objects::isNull);

        ScoreComposer.Result result = composer.compose(config, inputs);

        AnnualPerformance snapshot = existing == null ? new AnnualPerformance() : existing;
        snapshot.setCompanyId(companyId);
        snapshot.setEmployeeId(employeeId);
        snapshot.setPerfYear((short) year);
        snapshot.setPeriodStart(start);
        snapshot.setPeriodEnd(end);
        snapshot.setQuartersIncluded(quarters.size());
        snapshot.setQuarterlyAverageScore(quarterlyAverage);
        snapshot.setKraScore(inputs.get(PerformanceParameter.KRA_ACHIEVEMENT));
        snapshot.setKpiScore(inputs.get(PerformanceParameter.KPI_ACHIEVEMENT));
        snapshot.setManagerRating(inputs.get(PerformanceParameter.MANAGER_RATING));
        snapshot.setDepartmentHeadRating(inputs.get(PerformanceParameter.DEPARTMENT_HEAD_RATING));
        snapshot.setSelfAssessmentScore(inputs.get(PerformanceParameter.SELF_ASSESSMENT));
        applyResult(snapshot, result, companyId);

        AnnualPerformance saved = annualRepository.save(snapshot);
        announce(companyId, employeeId, "annual", String.valueOf(year), saved);
        return saved;
    }

    // ------------------------------------------------------------ internals

    /**
     * Tells the employee their period score is in. Daily scores are computed
     * every night for everyone, so they are deliberately silent: a daily
     * notification for every person every day would train people to ignore the
     * feed, which is where the messages that matter live.
     */
    private void announce(Long companyId, Long employeeId, String scope, String periodLabel,
                          PerformanceSnapshot snapshot) {
        if (snapshot.getOverallScore() == null) {
            return;
        }
        String band = snapshot.getRatingBandId() == null ? null
                : bandService.nameOf(snapshot.getRatingBandId());
        events.publishEvent(NotificationRaised
                .to(companyId, "SCORE_PUBLISHED")
                .recipient(employeeId)
                .about(scope, snapshot.getId())
                .with("scope", scope)
                .with("periodLabel", periodLabel)
                .with("score", snapshot.getOverallScore().toPlainString())
                .with("bandSuffix", band == null ? "" : " (" + band + ")")
                .build());
    }

    private String monthLabel(int year, int month) {
        return java.time.Month.of(month)
                .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + year;
    }

    private void applyResult(com.company.kra.modules.performance.entity.PerformanceSnapshot snapshot,
                             ScoreComposer.Result result, Long companyId) {
        snapshot.setOverallScore(result.score());
        snapshot.setFormulaVersion(result.formulaVersion());
        snapshot.setScoreBreakdown(serialise(result));
        snapshot.setComputedAt(LocalDateTime.now());
        snapshot.setRatingBandId(result.score() == null ? null
                : bandService.bandFor(companyId, result.score()).map(band -> band.getId()).orElse(null));
        if (snapshot.getStatus() == null) {
            snapshot.setStatus(PerformanceStatus.COMPUTED);
        }
    }

    /**
     * The breakdown is stored as JSON rather than columns because it is a
     * record of how a number was reached, not queryable state.
     */
    private String serialise(ScoreComposer.Result result) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "formulaVersion", result.formulaVersion(),
                    "measuredWeight", result.measuredWeight(),
                    "penaltyApplied", result.penaltyApplied() == null
                            ? BigDecimal.ZERO : result.penaltyApplied(),
                    "parameters", result.contributions()));
        } catch (JsonProcessingException ex) {
            log.error("Could not serialise the score breakdown", ex);
            return null;
        }
    }

    private BigDecimal productivityPercent(int productiveMinutes, int expectedMinutes) {
        if (expectedMinutes <= 0) {
            return null;
        }
        // Capped at 100: working beyond the contracted day is overtime, not
        // productivity worth more than a full day.
        return BigDecimal.valueOf(productiveMinutes)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(expectedMinutes), 2, RoundingMode.HALF_UP)
                .min(HUNDRED);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private void assertNotFrozen(PerformanceStatus status, String grain, String period) {
        if (status != null && status.isFrozen()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "The " + grain + " score for " + period
                            + " is finalised and cannot be recomputed. Reopen it first.");
        }
    }
}
