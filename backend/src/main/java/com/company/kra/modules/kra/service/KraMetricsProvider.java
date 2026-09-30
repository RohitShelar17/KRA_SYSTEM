package com.company.kra.modules.kra.service;

import com.company.kra.common.metrics.KraMetrics;
import com.company.kra.modules.kra.entity.EmployeeKpi;
import com.company.kra.modules.kra.entity.EmployeeKra;
import com.company.kra.modules.kra.repository.EmployeeKraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Goal achievement over an arbitrary window, for the scoring engine.
 *
 * <p>A KRA counts towards a window if its period overlaps it, which is what
 * lets a monthly score read a quarterly KRA. The same exclusion rule as the
 * scorecard applies throughout: anything unmeasurable is left out rather than
 * scored zero.</p>
 */
@Service
@RequiredArgsConstructor
public class KraMetricsProvider implements KraMetrics {

    private final EmployeeKraRepository kraRepository;
    private final KraProgressReader progressReader;
    private final AchievementCalculator calculator;

    @Override
    @Transactional(readOnly = true)
    public BigDecimal kraAchievementPercent(Long employeeId, LocalDate from, LocalDate to) {
        List<EmployeeKra> kras = overlapping(employeeId, from, to);
        if (kras.isEmpty()) {
            return null;
        }

        List<BigDecimal> scores = new ArrayList<>();
        List<BigDecimal> weights = new ArrayList<>();
        for (EmployeeKra kra : kras) {
            scores.add(kraScore(kra));
            weights.add(kra.getWeightage());
        }
        return calculator.overall(scores, weights);
    }

    /**
     * Every scorable KPI across those KRAs, weighted by the KPI's own weight
     * scaled by its KRA's — so a KPI inside a 30%-weighted KRA counts for more
     * than the same KPI inside a 10% one.
     */
    @Override
    @Transactional(readOnly = true)
    public BigDecimal kpiAchievementPercent(Long employeeId, LocalDate from, LocalDate to) {
        List<EmployeeKra> kras = overlapping(employeeId, from, to);
        if (kras.isEmpty()) {
            return null;
        }

        List<AchievementCalculator.KpiOutcome> outcomes = new ArrayList<>();
        for (EmployeeKra kra : kras) {
            for (EmployeeKpi kpi : kra.getKpis()) {
                BigDecimal achievement = progressReader.achievementFor(kpi, kra);
                BigDecimal weight = kpi.getWeightage()
                        .multiply(kra.getWeightage())
                        .divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP);

                outcomes.add(achievement == null
                        ? AchievementCalculator.KpiOutcome.excluded(weight, "Not measurable")
                        : AchievementCalculator.KpiOutcome.scored(achievement, weight));
            }
        }
        return calculator.rollUp(outcomes);
    }

    private BigDecimal kraScore(EmployeeKra kra) {
        List<AchievementCalculator.KpiOutcome> outcomes = new ArrayList<>();
        for (EmployeeKpi kpi : kra.getKpis()) {
            BigDecimal achievement = progressReader.achievementFor(kpi, kra);
            outcomes.add(achievement == null
                    ? AchievementCalculator.KpiOutcome.excluded(kpi.getWeightage(), "Not measurable")
                    : AchievementCalculator.KpiOutcome.scored(achievement, kpi.getWeightage()));
        }
        return calculator.rollUp(outcomes);
    }

    private List<EmployeeKra> overlapping(Long employeeId, LocalDate from, LocalDate to) {
        return kraRepository.findOverlapping(employeeId, from, to);
    }
}
