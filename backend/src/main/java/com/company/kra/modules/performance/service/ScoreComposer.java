package com.company.kra.modules.performance.service;

import com.company.kra.common.enums.PerformanceParameter;
import com.company.kra.modules.performance.entity.PerformanceFormulaConfig;
import com.company.kra.modules.performance.entity.PerformanceFormulaParameter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a formula configuration to a set of measured inputs.
 *
 * <p>Pure arithmetic, no repositories: given the weights and the raw values it
 * produces the score and the breakdown that explains it. That breakdown is
 * what an employee sees when they ask why they scored 84 rather than 86, so it
 * records every parameter — including the ones that could not be measured and
 * why.</p>
 *
 * <p>A parameter with no input is excluded and its weight redistributed across
 * the rest, exactly as an untargeted KPI is in Phase 3. Scoring a missing
 * manager rating as zero would turn an unfilled form into a bad appraisal.</p>
 */
@Slf4j
@Component
public class ScoreComposer {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** One line of the breakdown: what was measured, at what weight, and what it added. */
    public record Contribution(String parameterCode, String label, BigDecimal rawValue,
                               BigDecimal weightage, BigDecimal effectiveWeightage,
                               BigDecimal contribution, boolean measured, String skippedReason) {
    }

    public record Result(BigDecimal score, List<Contribution> contributions,
                         BigDecimal measuredWeight, int formulaVersion, BigDecimal penaltyApplied) {

        public boolean scored() {
            return score != null;
        }
    }

    /**
     * @param inputs raw 0-100 values per parameter; a null or absent value means
     *               the parameter could not be measured for this period
     */
    public Result compose(PerformanceFormulaConfig config,
                          Map<PerformanceParameter, BigDecimal> inputs) {
        return compose(config, inputs, null);
    }

    /**
     * @param submissionRatePct work-log submission rate for the period, used for
     *                          the consistency penalty; null skips the penalty
     */
    public Result compose(PerformanceFormulaConfig config,
                          Map<PerformanceParameter, BigDecimal> inputs,
                          BigDecimal submissionRatePct) {

        Map<PerformanceParameter, BigDecimal> values = inputs == null
                ? new EnumMap<>(PerformanceParameter.class) : inputs;

        // First pass establishes how much of the formula can actually be measured.
        BigDecimal measuredWeight = BigDecimal.ZERO;
        for (PerformanceFormulaParameter parameter : config.getParameters()) {
            if (values.get(parameter.getParameterCode()) != null) {
                measuredWeight = measuredWeight.add(parameter.getWeightage());
            }
        }

        List<Contribution> contributions = new ArrayList<>();

        if (measuredWeight.compareTo(BigDecimal.ZERO) == 0) {
            for (PerformanceFormulaParameter parameter : config.getParameters()) {
                contributions.add(skipped(parameter));
            }
            return new Result(null, contributions, BigDecimal.ZERO, config.getVersion(), null);
        }

        // Second pass scales the surviving weights back up to 100 between them,
        // so an unmeasurable parameter costs nobody anything.
        BigDecimal total = BigDecimal.ZERO;
        for (PerformanceFormulaParameter parameter : config.getParameters()) {
            BigDecimal raw = values.get(parameter.getParameterCode());
            if (raw == null) {
                contributions.add(skipped(parameter));
                continue;
            }

            BigDecimal effective = parameter.getWeightage()
                    .multiply(HUNDRED)
                    .divide(measuredWeight, 6, RoundingMode.HALF_UP);
            BigDecimal contribution = raw.multiply(effective)
                    .divide(HUNDRED, 6, RoundingMode.HALF_UP);
            total = total.add(contribution);

            contributions.add(new Contribution(parameter.getParameterCode().name(),
                    parameter.getLabel(), raw.setScale(SCALE, RoundingMode.HALF_UP),
                    parameter.getWeightage(), effective.setScale(SCALE, RoundingMode.HALF_UP),
                    contribution.setScale(SCALE, RoundingMode.HALF_UP), true, null));
        }

        BigDecimal penalty = penaltyFor(config, submissionRatePct);
        BigDecimal score = total.subtract(penalty).max(BigDecimal.ZERO)
                .setScale(SCALE, RoundingMode.HALF_UP);

        return new Result(score, contributions, measuredWeight, config.getVersion(),
                penalty.compareTo(BigDecimal.ZERO) == 0 ? null : penalty);
    }

    private Contribution skipped(PerformanceFormulaParameter parameter) {
        return new Contribution(parameter.getParameterCode().name(), parameter.getLabel(), null,
                parameter.getWeightage(), BigDecimal.ZERO, BigDecimal.ZERO, false,
                reasonFor(parameter.getParameterCode()));
    }

    /**
     * The consistency penalty: someone who files too few work logs has not
     * produced enough evidence for the rest of the figures to stand on.
     */
    private BigDecimal penaltyFor(PerformanceFormulaConfig config, BigDecimal submissionRatePct) {
        if (submissionRatePct == null
                || config.getMinSubmissionRatePct().compareTo(BigDecimal.ZERO) <= 0
                || config.getSubmissionPenaltyPct().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (submissionRatePct.compareTo(config.getMinSubmissionRatePct()) >= 0) {
            return BigDecimal.ZERO;
        }
        return config.getSubmissionPenaltyPct();
    }

    private String reasonFor(PerformanceParameter parameter) {
        return switch (parameter) {
            case QUALITY_RATING, MANAGER_RATING -> "No manager rating recorded for this period";
            case DEPARTMENT_HEAD_RATING -> "No department head rating recorded";
            case SELF_ASSESSMENT -> "No self assessment submitted";
            case KRA_ACHIEVEMENT -> "No KRAs assigned, or nothing measurable yet";
            case KPI_ACHIEVEMENT -> "No KPIs with both a target and an actual";
            case TASK_COMPLETION -> "No tasks assigned in this period";
            case PRODUCTIVITY -> "No time logged in this period";
            case DAILY_AVERAGE -> "No daily scores computed for this period";
            case MONTHLY_AVERAGE -> "No monthly scores computed for this period";
            case QUARTERLY_AVERAGE -> "No quarterly scores computed for this year";
        };
    }
}
