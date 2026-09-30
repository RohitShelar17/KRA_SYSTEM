package com.company.kra.modules.kra.service;

import com.company.kra.common.metrics.TaskMetrics;
import com.company.kra.common.metrics.TimesheetMetrics;
import com.company.kra.modules.kra.entity.EmployeeKpi;
import com.company.kra.modules.kra.entity.EmployeeKra;
import com.company.kra.modules.kra.entity.KpiActual;
import com.company.kra.modules.kra.entity.KpiTarget;
import com.company.kra.modules.kra.repository.KpiActualRepository;
import com.company.kra.modules.kra.repository.KpiTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Resolves one KPI's target and actual for its period, and the achievement
 * that follows.
 *
 * <p>Extracted from the scorecard service because the scoring engine needs the
 * same numbers: there must be exactly one place that decides what a KPI
 * achieved, or the score on the appraisal screen and the score in the snapshot
 * could disagree.</p>
 */
@Component
@RequiredArgsConstructor
public class KraProgressReader {

    private final KpiTargetRepository targetRepository;
    private final KpiActualRepository actualRepository;
    private final AchievementCalculator calculator;
    private final TimesheetMetrics timesheetMetrics;
    private final TaskMetrics taskMetrics;

    /** What the KPI is aiming at this period, summing finer-grained targets if that is how it was set. */
    @Transactional(readOnly = true)
    public BigDecimal targetFor(EmployeeKpi kpi, EmployeeKra kra) {
        Optional<KpiTarget> exact = targetRepository.findByEmployeeKpiIdAndPeriodStartAndPeriodEnd(
                kpi.getId(), kra.getPeriodStart(), kra.getPeriodEnd());
        if (exact.isPresent()) {
            return exact.get().getTargetValue();
        }

        List<KpiTarget> within = targetRepository.findWithin(List.of(kpi.getId()),
                kra.getPeriodStart(), kra.getPeriodEnd());
        if (within.isEmpty()) {
            return null;
        }
        return within.stream().map(KpiTarget::getTargetValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public Optional<KpiActual> storedActual(EmployeeKpi kpi, EmployeeKra kra) {
        return actualRepository.findByEmployeeKpiIdAndPeriodStartAndPeriodEnd(
                kpi.getId(), kra.getPeriodStart(), kra.getPeriodEnd());
    }

    /** What was achieved: measured from recorded work if the KPI says so, otherwise the stored figure. */
    @Transactional(readOnly = true)
    public BigDecimal actualFor(EmployeeKpi kpi, EmployeeKra kra) {
        if (!kpi.getActualSource().isDerived()) {
            return storedActual(kpi, kra).map(KpiActual::getActualValue).orElse(null);
        }

        LocalDate start = kra.getPeriodStart();
        // Never measure into the future: a quarter still running is scored on
        // what has happened so far.
        LocalDate end = kra.getPeriodEnd().isAfter(LocalDate.now())
                ? LocalDate.now() : kra.getPeriodEnd();
        if (end.isBefore(start)) {
            return null;
        }

        return switch (kpi.getActualSource()) {
            case WORK_LOG_PRODUCTIVE_HOURS ->
                    timesheetMetrics.productiveHoursRatioPercent(kpi.getEmployeeId(), start, end);
            case TASK_COMPLETION_RATE ->
                    taskMetrics.completionRatePercent(kpi.getEmployeeId(), start, end);
            case TASK_ON_TIME_RATE ->
                    taskMetrics.onTimeRatePercent(kpi.getEmployeeId(), start, end);
            default -> null;
        };
    }

    /** Null when the KPI has no target or no actual, so callers can exclude it. */
    @Transactional(readOnly = true)
    public BigDecimal achievementFor(EmployeeKpi kpi, EmployeeKra kra) {
        return outcomeFor(kpi, kra).achievementPercent();
    }

    @Transactional(readOnly = true)
    public AchievementCalculator.KpiOutcome outcomeFor(EmployeeKpi kpi, EmployeeKra kra) {
        return calculator.achievement(targetFor(kpi, kra), actualFor(kpi, kra),
                kpi.getTargetDirection(), kpi.getCapPercent(), kpi.getWeightage());
    }
}
