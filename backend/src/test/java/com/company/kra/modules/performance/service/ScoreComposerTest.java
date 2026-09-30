package com.company.kra.modules.performance.service;

import com.company.kra.common.enums.PerformanceParameter;
import com.company.kra.common.enums.PerformanceScope;
import com.company.kra.modules.performance.entity.PerformanceFormulaConfig;
import com.company.kra.modules.performance.entity.PerformanceFormulaParameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scoring arithmetic and, just as importantly, the breakdown that explains
 * it. These numbers go into appraisals, so each rule is pinned on its own.
 */
class ScoreComposerTest {

    private final ScoreComposer composer = new ScoreComposer();

    @Nested
    @DisplayName("Weighted composition")
    class Composition {

        @Test
        @DisplayName("The blueprint's daily formula produces its stated score")
        void blueprintDailyFormula() {
            // 30/25/20/15/10 across the five daily parameters
            PerformanceFormulaConfig config = dailyConfig();
            Map<PerformanceParameter, BigDecimal> inputs = new EnumMap<>(PerformanceParameter.class);
            inputs.put(PerformanceParameter.TASK_COMPLETION, new BigDecimal("90"));
            inputs.put(PerformanceParameter.KRA_ACHIEVEMENT, new BigDecimal("80"));
            inputs.put(PerformanceParameter.PRODUCTIVITY, new BigDecimal("100"));
            inputs.put(PerformanceParameter.QUALITY_RATING, new BigDecimal("70"));
            inputs.put(PerformanceParameter.MANAGER_RATING, new BigDecimal("60"));

            // 27 + 20 + 20 + 10.5 + 6 = 83.5
            ScoreComposer.Result result = composer.compose(config, inputs);

            assertThat(result.score()).isEqualByComparingTo("83.50");
            assertThat(result.measuredWeight()).isEqualByComparingTo("100");
            assertThat(result.contributions()).hasSize(5);
        }

        @Test
        @DisplayName("Every parameter's contribution is recorded, so the score can be explained")
        void breakdownExplainsTheScore() {
            Map<PerformanceParameter, BigDecimal> inputs = new EnumMap<>(PerformanceParameter.class);
            inputs.put(PerformanceParameter.TASK_COMPLETION, new BigDecimal("90"));
            inputs.put(PerformanceParameter.KRA_ACHIEVEMENT, new BigDecimal("80"));
            inputs.put(PerformanceParameter.PRODUCTIVITY, new BigDecimal("100"));
            inputs.put(PerformanceParameter.QUALITY_RATING, new BigDecimal("70"));
            inputs.put(PerformanceParameter.MANAGER_RATING, new BigDecimal("60"));

            ScoreComposer.Result result = composer.compose(dailyConfig(), inputs);

            ScoreComposer.Contribution taskLine = result.contributions().stream()
                    .filter(c -> c.parameterCode().equals("TASK_COMPLETION"))
                    .findFirst()
                    .orElseThrow();

            assertThat(taskLine.measured()).isTrue();
            assertThat(taskLine.rawValue()).isEqualByComparingTo("90");
            assertThat(taskLine.weightage()).isEqualByComparingTo("30");
            assertThat(taskLine.contribution()).isEqualByComparingTo("27.00");

            BigDecimal summed = result.contributions().stream()
                    .map(ScoreComposer.Contribution::contribution)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(summed).isEqualByComparingTo(result.score());
        }
    }

    @Nested
    @DisplayName("Unmeasurable parameters")
    class Redistribution {

        @Test
        @DisplayName("A missing manager rating redistributes rather than scoring zero")
        void missingRatingRedistributes() {
            Map<PerformanceParameter, BigDecimal> inputs = new EnumMap<>(PerformanceParameter.class);
            inputs.put(PerformanceParameter.TASK_COMPLETION, new BigDecimal("90"));
            inputs.put(PerformanceParameter.KRA_ACHIEVEMENT, new BigDecimal("80"));
            inputs.put(PerformanceParameter.PRODUCTIVITY, new BigDecimal("100"));

            // Only 75 of the 100 weight is measurable, so the three surviving
            // parameters scale up to 40 / 33.33 / 26.67:
            // 90*0.40 + 80*0.3333 + 100*0.2667 = 36 + 26.67 + 26.67
            ScoreComposer.Result result = composer.compose(dailyConfig(), inputs);

            assertThat(result.measuredWeight()).isEqualByComparingTo("75");
            assertThat(result.score()).isEqualByComparingTo("89.33");
        }

        @Test
        @DisplayName("An unmeasured parameter still appears in the breakdown, with the reason")
        void skippedParameterIsExplained() {
            Map<PerformanceParameter, BigDecimal> inputs = new EnumMap<>(PerformanceParameter.class);
            inputs.put(PerformanceParameter.TASK_COMPLETION, new BigDecimal("90"));

            ScoreComposer.Result result = composer.compose(dailyConfig(), inputs);

            ScoreComposer.Contribution managerLine = result.contributions().stream()
                    .filter(c -> c.parameterCode().equals("MANAGER_RATING"))
                    .findFirst()
                    .orElseThrow();

            assertThat(managerLine.measured()).isFalse();
            assertThat(managerLine.rawValue()).isNull();
            assertThat(managerLine.contribution()).isEqualByComparingTo("0");
            assertThat(managerLine.skippedReason()).contains("No manager rating");
        }

        @Test
        @DisplayName("One measurable parameter carries the whole score")
        void singleParameterCarriesTheScore() {
            Map<PerformanceParameter, BigDecimal> inputs = new EnumMap<>(PerformanceParameter.class);
            inputs.put(PerformanceParameter.PRODUCTIVITY, new BigDecimal("64"));

            ScoreComposer.Result result = composer.compose(dailyConfig(), inputs);

            assertThat(result.score()).isEqualByComparingTo("64.00");
            assertThat(result.measuredWeight()).isEqualByComparingTo("20");
        }

        @Test
        @DisplayName("Nothing measurable yields no score at all, not a zero")
        void nothingMeasurableYieldsNoScore() {
            ScoreComposer.Result result = composer.compose(dailyConfig(),
                    new EnumMap<>(PerformanceParameter.class));

            assertThat(result.score()).isNull();
            assertThat(result.scored()).isFalse();
            assertThat(result.contributions()).hasSize(5);
            assertThat(result.contributions()).allMatch(c -> !c.measured());
        }
    }

    @Nested
    @DisplayName("Submission penalty")
    class Penalty {

        @Test
        @DisplayName("Filing too few work logs costs the configured penalty")
        void penaltyAppliesBelowThreshold() {
            PerformanceFormulaConfig config = dailyConfig();
            config.setMinSubmissionRatePct(new BigDecimal("80"));
            config.setSubmissionPenaltyPct(new BigDecimal("5"));

            Map<PerformanceParameter, BigDecimal> inputs = new EnumMap<>(PerformanceParameter.class);
            inputs.put(PerformanceParameter.PRODUCTIVITY, new BigDecimal("90"));

            ScoreComposer.Result result = composer.compose(config, inputs, new BigDecimal("60"));

            assertThat(result.score()).isEqualByComparingTo("85.00");
            assertThat(result.penaltyApplied()).isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("Meeting the threshold costs nothing")
        void noPenaltyAtOrAboveThreshold() {
            PerformanceFormulaConfig config = dailyConfig();
            config.setMinSubmissionRatePct(new BigDecimal("80"));
            config.setSubmissionPenaltyPct(new BigDecimal("5"));

            Map<PerformanceParameter, BigDecimal> inputs = new EnumMap<>(PerformanceParameter.class);
            inputs.put(PerformanceParameter.PRODUCTIVITY, new BigDecimal("90"));

            ScoreComposer.Result result = composer.compose(config, inputs, new BigDecimal("80"));

            assertThat(result.score()).isEqualByComparingTo("90.00");
            assertThat(result.penaltyApplied()).isNull();
        }

        @Test
        @DisplayName("A penalty cannot push a score below zero")
        void penaltyFloorsAtZero() {
            PerformanceFormulaConfig config = dailyConfig();
            config.setMinSubmissionRatePct(new BigDecimal("80"));
            config.setSubmissionPenaltyPct(new BigDecimal("50"));

            Map<PerformanceParameter, BigDecimal> inputs = new EnumMap<>(PerformanceParameter.class);
            inputs.put(PerformanceParameter.PRODUCTIVITY, new BigDecimal("10"));

            ScoreComposer.Result result = composer.compose(config, inputs, new BigDecimal("10"));

            assertThat(result.score()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("With no threshold configured the penalty never applies")
        void disabledPenaltyIsIgnored() {
            Map<PerformanceParameter, BigDecimal> inputs = new EnumMap<>(PerformanceParameter.class);
            inputs.put(PerformanceParameter.PRODUCTIVITY, new BigDecimal("90"));

            ScoreComposer.Result result = composer.compose(dailyConfig(), inputs, new BigDecimal("10"));

            assertThat(result.score()).isEqualByComparingTo("90.00");
            assertThat(result.penaltyApplied()).isNull();
        }
    }

    @Test
    @DisplayName("The formula version travels with the result")
    void formulaVersionIsRecorded() {
        PerformanceFormulaConfig config = dailyConfig();
        config.setVersion(7);

        Map<PerformanceParameter, BigDecimal> inputs = new EnumMap<>(PerformanceParameter.class);
        inputs.put(PerformanceParameter.PRODUCTIVITY, new BigDecimal("50"));

        assertThat(composer.compose(config, inputs).formulaVersion()).isEqualTo(7);
    }

    // ------------------------------------------------------------- fixtures

    private PerformanceFormulaConfig dailyConfig() {
        PerformanceFormulaConfig config = new PerformanceFormulaConfig();
        config.setId(1L);
        config.setCompanyId(1L);
        config.setScopeType(PerformanceScope.DAILY);
        config.setVersion(1);
        config.setName("Daily v1");

        addParameter(config, PerformanceParameter.TASK_COMPLETION, "Task completion", "30");
        addParameter(config, PerformanceParameter.KRA_ACHIEVEMENT, "KRA achievement", "25");
        addParameter(config, PerformanceParameter.PRODUCTIVITY, "Productive hours", "20");
        addParameter(config, PerformanceParameter.QUALITY_RATING, "Quality rating", "15");
        addParameter(config, PerformanceParameter.MANAGER_RATING, "Manager rating", "10");
        return config;
    }

    private void addParameter(PerformanceFormulaConfig config, PerformanceParameter code,
                              String label, String weightage) {
        PerformanceFormulaParameter parameter = new PerformanceFormulaParameter();
        parameter.setCompanyId(1L);
        parameter.setConfig(config);
        parameter.setParameterCode(code);
        parameter.setLabel(label);
        parameter.setWeightage(new BigDecimal(weightage));
        parameter.setDisplayOrder(config.getParameters().size() * 10);
        config.getParameters().add(parameter);
    }
}
