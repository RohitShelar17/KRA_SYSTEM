package com.company.kra.modules.kra.service;

import com.company.kra.common.enums.TargetDirection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Turns targets and actuals into achievement percentages, and rolls those up
 * into KRA and overall scores.
 *
 * <p>Pure arithmetic with no repository access, so every rule below is
 * testable on its own — which matters, because these numbers end up in
 * somebody's appraisal.</p>
 */
@Slf4j
@Component
public class AchievementCalculator {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** One KPI's contribution, or an explicit reason it has none. */
    public record KpiOutcome(BigDecimal achievementPercent, BigDecimal weightage, boolean scored,
                             String excludedReason) {

        public static KpiOutcome excluded(BigDecimal weightage, String reason) {
            return new KpiOutcome(null, weightage, false, reason);
        }

        public static KpiOutcome scored(BigDecimal achievement, BigDecimal weightage) {
            return new KpiOutcome(achievement, weightage, true, null);
        }
    }

    /**
     * Achievement for a single KPI.
     *
     * @param target  the number to hit; null means the KPI has no target this period
     * @param actual  what was recorded; null means nothing was recorded yet
     * @param cap     ceiling on over-achievement, e.g. 120%
     */
    public KpiOutcome achievement(BigDecimal target, BigDecimal actual, TargetDirection direction,
                                  BigDecimal cap, BigDecimal weightage) {

        if (target == null) {
            // A KPI nobody set a target for must not score zero: that would
            // punish the employee for an administrative omission.
            return KpiOutcome.excluded(weightage, "No target set for this period");
        }
        if (actual == null) {
            return KpiOutcome.excluded(weightage, "No actual recorded yet");
        }

        BigDecimal ceiling = cap == null ? new BigDecimal("120") : cap;
        BigDecimal percent = switch (direction) {
            case BOOLEAN -> actual.compareTo(BigDecimal.ZERO) > 0 ? HUNDRED : BigDecimal.ZERO;
            case HIGHER_IS_BETTER -> higherIsBetter(target, actual);
            case LOWER_IS_BETTER -> lowerIsBetter(target, actual, ceiling);
        };

        BigDecimal bounded = percent.min(ceiling).max(BigDecimal.ZERO);
        return KpiOutcome.scored(bounded.setScale(SCALE, RoundingMode.HALF_UP), weightage);
    }

    private BigDecimal higherIsBetter(BigDecimal target, BigDecimal actual) {
        if (target.compareTo(BigDecimal.ZERO) == 0) {
            // A zero target is met by definition; anything above it is a bonus
            // we cannot express as a ratio, so it lands at 100.
            return HUNDRED;
        }
        return actual.divide(target, 6, RoundingMode.HALF_UP).multiply(HUNDRED);
    }

    private BigDecimal lowerIsBetter(BigDecimal target, BigDecimal actual, BigDecimal ceiling) {
        if (actual.compareTo(BigDecimal.ZERO) <= 0) {
            // Nothing went wrong at all - no escaped defects, no reopened bugs.
            // The ratio is undefined, so award the best score the cap allows.
            return ceiling;
        }
        if (target.compareTo(BigDecimal.ZERO) <= 0) {
            // The goal was "none of this", and some happened.
            return BigDecimal.ZERO;
        }
        return target.divide(actual, 6, RoundingMode.HALF_UP).multiply(HUNDRED);
    }

    /**
     * Weighted mean of the KPI outcomes that could be scored.
     *
     * <p>Excluded KPIs do not drag the score down; their weight is dropped and
     * the remainder is renormalised, so a KRA with one untargeted KPI is scored
     * on the rest rather than on a hole.</p>
     */
    public BigDecimal rollUp(List<KpiOutcome> outcomes) {
        BigDecimal weightSum = BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;

        for (KpiOutcome outcome : outcomes) {
            if (!outcome.scored()) {
                continue;
            }
            BigDecimal weight = outcome.weightage() == null ? BigDecimal.ZERO : outcome.weightage();
            weightSum = weightSum.add(weight);
            weighted = weighted.add(outcome.achievementPercent().multiply(weight));
        }

        if (weightSum.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return weighted.divide(weightSum, SCALE, RoundingMode.HALF_UP);
    }

    /** Weighted mean of KRA scores, skipping any KRA that could not be scored. */
    public BigDecimal overall(List<BigDecimal> scores, List<BigDecimal> weights) {
        if (scores.size() != weights.size()) {
            throw new IllegalArgumentException("Scores and weights must line up");
        }
        BigDecimal weightSum = BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;

        for (int index = 0; index < scores.size(); index++) {
            BigDecimal score = scores.get(index);
            if (score == null) {
                continue;
            }
            BigDecimal weight = weights.get(index) == null ? BigDecimal.ZERO : weights.get(index);
            weightSum = weightSum.add(weight);
            weighted = weighted.add(score.multiply(weight));
        }

        if (weightSum.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return weighted.divide(weightSum, SCALE, RoundingMode.HALF_UP);
    }
}
