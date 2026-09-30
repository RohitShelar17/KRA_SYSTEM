package com.company.kra.modules.kra.service;

import com.company.kra.common.enums.TargetDirection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scoring arithmetic. Every rule is tested on its own because these
 * numbers end up in an appraisal conversation, where "the system said so" is
 * not an acceptable explanation.
 */
class AchievementCalculatorTest {

    private final AchievementCalculator calculator = new AchievementCalculator();

    private static final BigDecimal CAP = new BigDecimal("120");
    private static final BigDecimal WEIGHT = new BigDecimal("100");

    @Nested
    @DisplayName("Higher is better")
    class HigherIsBetter {

        @Test
        @DisplayName("Hitting the target exactly scores 100")
        void exactlyOnTarget() {
            var outcome = score("100", "100", TargetDirection.HIGHER_IS_BETTER);
            assertThat(outcome.achievementPercent()).isEqualByComparingTo("100.00");
            assertThat(outcome.scored()).isTrue();
        }

        @Test
        @DisplayName("Falling short scores in proportion")
        void underTarget() {
            // 8.5 lakh against a 10 lakh target
            var outcome = score("1000000", "850000", TargetDirection.HIGHER_IS_BETTER);
            assertThat(outcome.achievementPercent()).isEqualByComparingTo("85.00");
        }

        @Test
        @DisplayName("Over-achievement is rewarded up to the cap")
        void overTargetWithinCap() {
            var outcome = score("100", "110", TargetDirection.HIGHER_IS_BETTER);
            assertThat(outcome.achievementPercent()).isEqualByComparingTo("110.00");
        }

        @Test
        @DisplayName("Over-achievement is bounded by the cap")
        void overTargetBeyondCap() {
            var outcome = score("100", "300", TargetDirection.HIGHER_IS_BETTER);
            assertThat(outcome.achievementPercent()).isEqualByComparingTo("120.00");
        }

        @Test
        @DisplayName("Zero actual scores zero, not a division error")
        void zeroActual() {
            var outcome = score("100", "0", TargetDirection.HIGHER_IS_BETTER);
            assertThat(outcome.achievementPercent()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("A zero target counts as met rather than dividing by zero")
        void zeroTarget() {
            var outcome = score("0", "5", TargetDirection.HIGHER_IS_BETTER);
            assertThat(outcome.achievementPercent()).isEqualByComparingTo("100.00");
        }
    }

    @Nested
    @DisplayName("Lower is better")
    class LowerIsBetter {

        @Test
        @DisplayName("Beating the target scores above 100")
        void beatingTheTarget() {
            // Resolution time of 12 hours against a 24-hour target
            var outcome = score("24", "12", TargetDirection.LOWER_IS_BETTER);
            assertThat(outcome.achievementPercent()).isEqualByComparingTo("120.00");
        }

        @Test
        @DisplayName("Missing the target scores below 100")
        void missingTheTarget() {
            // 10% reopened bugs against a 5% target
            var outcome = score("5", "10", TargetDirection.LOWER_IS_BETTER);
            assertThat(outcome.achievementPercent()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("Exactly on target scores 100")
        void exactlyOnTarget() {
            var outcome = score("24", "24", TargetDirection.LOWER_IS_BETTER);
            assertThat(outcome.achievementPercent()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("Zero actual is the best possible result, capped")
        void zeroActualIsPerfect() {
            // No escaped defects at all: the ratio is undefined, so the cap applies.
            var outcome = score("1", "0", TargetDirection.LOWER_IS_BETTER);
            assertThat(outcome.achievementPercent()).isEqualByComparingTo("120.00");
        }

        @Test
        @DisplayName("A zero target with a non-zero actual scores zero")
        void zeroTargetMissed() {
            var outcome = score("0", "3", TargetDirection.LOWER_IS_BETTER);
            assertThat(outcome.achievementPercent()).isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Boolean KPIs")
    class Booleans {

        @Test
        @DisplayName("Met scores 100")
        void met() {
            assertThat(score("1", "1", TargetDirection.BOOLEAN).achievementPercent())
                    .isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("Not met scores zero")
        void notMet() {
            assertThat(score("1", "0", TargetDirection.BOOLEAN).achievementPercent())
                    .isEqualByComparingTo("0.00");
        }
    }

    @Nested
    @DisplayName("Missing data")
    class MissingData {

        @Test
        @DisplayName("No target excludes the KPI rather than scoring it zero")
        void noTarget() {
            var outcome = calculator.achievement(null, new BigDecimal("50"),
                    TargetDirection.HIGHER_IS_BETTER, CAP, WEIGHT);

            assertThat(outcome.scored()).isFalse();
            assertThat(outcome.achievementPercent()).isNull();
            assertThat(outcome.excludedReason()).contains("No target set");
        }

        @Test
        @DisplayName("No actual excludes the KPI rather than scoring it zero")
        void noActual() {
            var outcome = calculator.achievement(new BigDecimal("90"), null,
                    TargetDirection.HIGHER_IS_BETTER, CAP, WEIGHT);

            assertThat(outcome.scored()).isFalse();
            assertThat(outcome.excludedReason()).contains("No actual recorded");
        }
    }

    @Nested
    @DisplayName("Roll-up")
    class RollUp {

        @Test
        @DisplayName("KPI scores combine by weight")
        void weightedMean() {
            // The blueprint's Bug Resolution example: 40/30/30 weighting.
            List<AchievementCalculator.KpiOutcome> outcomes = List.of(
                    AchievementCalculator.KpiOutcome.scored(new BigDecimal("100"), new BigDecimal("40")),
                    AchievementCalculator.KpiOutcome.scored(new BigDecimal("80"), new BigDecimal("30")),
                    AchievementCalculator.KpiOutcome.scored(new BigDecimal("60"), new BigDecimal("30")));

            // (100*40 + 80*30 + 60*30) / 100 = 82
            assertThat(calculator.rollUp(outcomes)).isEqualByComparingTo("82.00");
        }

        @Test
        @DisplayName("An excluded KPI has its weight redistributed, not zeroed")
        void excludedWeightRedistributed() {
            List<AchievementCalculator.KpiOutcome> outcomes = List.of(
                    AchievementCalculator.KpiOutcome.scored(new BigDecimal("90"), new BigDecimal("50")),
                    AchievementCalculator.KpiOutcome.excluded(new BigDecimal("50"), "No target"));

            // Scored on the half that could be measured, so 90 rather than 45.
            assertThat(calculator.rollUp(outcomes)).isEqualByComparingTo("90.00");
        }

        @Test
        @DisplayName("Nothing scoreable yields no score at all")
        void nothingScoreable() {
            List<AchievementCalculator.KpiOutcome> outcomes = List.of(
                    AchievementCalculator.KpiOutcome.excluded(new BigDecimal("100"), "No target"));

            assertThat(calculator.rollUp(outcomes)).isNull();
        }

        @Test
        @DisplayName("KRA scores combine into an overall by weight")
        void overallScore() {
            // 30/20/20/20/10 across the developer scorecard
            List<BigDecimal> scores = List.of(new BigDecimal("90"), new BigDecimal("80"),
                    new BigDecimal("100"), new BigDecimal("70"), new BigDecimal("60"));
            List<BigDecimal> weights = List.of(new BigDecimal("30"), new BigDecimal("20"),
                    new BigDecimal("20"), new BigDecimal("20"), new BigDecimal("10"));

            // 27 + 16 + 20 + 14 + 6 = 83
            assertThat(calculator.overall(scores, weights)).isEqualByComparingTo("83.00");
        }

        @Test
        @DisplayName("An unscored KRA drops out of the overall instead of counting as zero")
        void unscoredKraDropsOut() {
            List<BigDecimal> scores = new java.util.ArrayList<>(List.of(new BigDecimal("90")));
            scores.add(null);
            List<BigDecimal> weights = List.of(new BigDecimal("60"), new BigDecimal("40"));

            assertThat(calculator.overall(scores, weights)).isEqualByComparingTo("90.00");
        }

        @Test
        @DisplayName("Mismatched scores and weights is a programming error, not a silent result")
        void mismatchedInputs() {
            org.assertj.core.api.Assertions
                    .assertThatThrownBy(() -> calculator.overall(List.of(BigDecimal.ONE), List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private AchievementCalculator.KpiOutcome score(String target, String actual,
                                                   TargetDirection direction) {
        return calculator.achievement(new BigDecimal(target), new BigDecimal(actual), direction,
                CAP, WEIGHT);
    }
}
