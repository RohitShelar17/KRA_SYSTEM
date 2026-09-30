package com.company.kra.modules.appraisal.service;

import com.company.kra.common.enums.AppraisalStep;
import com.company.kra.common.enums.WorkflowStepType;
import com.company.kra.exception.BusinessException;
import com.company.kra.modules.appraisal.entity.ReviewWorkflowStep;
import com.company.kra.modules.appraisal.repository.ReviewWorkflowStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * The workflow is configuration, so the engine has to behave sensibly for
 * every shape an organisation might configure — including one that changes
 * while appraisals are in flight.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewWorkflowEngineTest {

    private static final Long COMPANY = 1L;

    @Mock
    private ReviewWorkflowStepRepository stepRepository;

    private ReviewWorkflowEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ReviewWorkflowEngine(stepRepository);
    }

    @Test
    @DisplayName("The default four-stage flow walks in order and then completes")
    void fullFlowWalksInOrder() {
        configure(step(10, WorkflowStepType.SELF_ASSESSMENT, true),
                step(20, WorkflowStepType.MANAGER_REVIEW, true),
                step(30, WorkflowStepType.DEPARTMENT_HEAD_REVIEW, false),
                step(40, WorkflowStepType.HR_REVIEW, true));

        assertThat(engine.firstStep(COMPANY)).isEqualTo(AppraisalStep.SELF_ASSESSMENT);
        assertThat(engine.nextStep(COMPANY, AppraisalStep.SELF_ASSESSMENT))
                .isEqualTo(AppraisalStep.MANAGER_REVIEW);
        assertThat(engine.nextStep(COMPANY, AppraisalStep.MANAGER_REVIEW))
                .isEqualTo(AppraisalStep.DEPARTMENT_HEAD_REVIEW);
        assertThat(engine.nextStep(COMPANY, AppraisalStep.DEPARTMENT_HEAD_REVIEW))
                .isEqualTo(AppraisalStep.HR_REVIEW);
        assertThat(engine.nextStep(COMPANY, AppraisalStep.HR_REVIEW))
                .isEqualTo(AppraisalStep.COMPLETED);
    }

    @Test
    @DisplayName("A flow without department heads skips straight from manager to HR")
    void inactiveStepIsNotInTheFlow() {
        configure(step(10, WorkflowStepType.SELF_ASSESSMENT, true),
                step(20, WorkflowStepType.MANAGER_REVIEW, true),
                step(40, WorkflowStepType.HR_REVIEW, true));

        assertThat(engine.nextStep(COMPANY, AppraisalStep.MANAGER_REVIEW))
                .isEqualTo(AppraisalStep.HR_REVIEW);
        assertThat(engine.isActive(COMPANY, WorkflowStepType.DEPARTMENT_HEAD_REVIEW)).isFalse();
    }

    @Test
    @DisplayName("A two-step flow completes after the manager review")
    void shortFlowCompletesEarly() {
        configure(step(10, WorkflowStepType.SELF_ASSESSMENT, true),
                step(20, WorkflowStepType.MANAGER_REVIEW, true));

        assertThat(engine.nextStep(COMPANY, AppraisalStep.MANAGER_REVIEW))
                .isEqualTo(AppraisalStep.COMPLETED);
    }

    @Test
    @DisplayName("A flow with no self assessment starts at the manager review")
    void flowCanStartWithReview() {
        configure(step(20, WorkflowStepType.MANAGER_REVIEW, true),
                step(40, WorkflowStepType.HR_REVIEW, true));

        assertThat(engine.firstStep(COMPANY)).isEqualTo(AppraisalStep.MANAGER_REVIEW);
    }

    @Test
    @DisplayName("An appraisal stranded on a deactivated step moves to the next configured one")
    void deactivatedCurrentStepDoesNotStrandTheAppraisal() {
        // The appraisal is sitting on DEPARTMENT_HEAD_REVIEW, which has since
        // been removed from the workflow.
        configure(step(10, WorkflowStepType.SELF_ASSESSMENT, true),
                step(20, WorkflowStepType.MANAGER_REVIEW, true),
                step(40, WorkflowStepType.HR_REVIEW, true));

        assertThat(engine.nextStep(COMPANY, AppraisalStep.DEPARTMENT_HEAD_REVIEW))
                .isEqualTo(AppraisalStep.HR_REVIEW);
    }

    @Test
    @DisplayName("An optional step can be skipped")
    void optionalStepIsSkippable() {
        configure(step(10, WorkflowStepType.SELF_ASSESSMENT, true),
                step(30, WorkflowStepType.DEPARTMENT_HEAD_REVIEW, false),
                step(40, WorkflowStepType.HR_REVIEW, true));

        assertThatCode(() -> engine.assertSkippable(COMPANY, AppraisalStep.DEPARTMENT_HEAD_REVIEW))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A required step cannot be skipped, and the message names it")
    void requiredStepIsNotSkippable() {
        configure(step(10, WorkflowStepType.SELF_ASSESSMENT, true),
                step(40, WorkflowStepType.HR_REVIEW, true));

        assertThatThrownBy(() -> engine.assertSkippable(COMPANY, AppraisalStep.HR_REVIEW))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("HR review")
                .hasMessageContaining("cannot be skipped");
    }

    @Test
    @DisplayName("A completed appraisal has nothing left to skip")
    void completedCannotBeSkipped() {
        configure(step(10, WorkflowStepType.SELF_ASSESSMENT, true));

        assertThatThrownBy(() -> engine.assertSkippable(COMPANY, AppraisalStep.COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already complete");
    }

    @Test
    @DisplayName("With no workflow configured the engine refuses rather than guessing")
    void missingWorkflowIsRefused() {
        when(stepRepository.findByCompanyIdAndActiveTrueOrderByStepOrderAsc(anyLong()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> engine.firstStep(COMPANY))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No review workflow is configured");
    }

    @Test
    @DisplayName("Position tracks progress through the flow")
    void positionReportsProgress() {
        configure(step(10, WorkflowStepType.SELF_ASSESSMENT, true),
                step(20, WorkflowStepType.MANAGER_REVIEW, true),
                step(40, WorkflowStepType.HR_REVIEW, true));

        assertThat(engine.positionOf(COMPANY, AppraisalStep.SELF_ASSESSMENT)).isZero();
        assertThat(engine.positionOf(COMPANY, AppraisalStep.MANAGER_REVIEW)).isEqualTo(1);
        assertThat(engine.positionOf(COMPANY, AppraisalStep.COMPLETED)).isEqualTo(3);
    }

    // ------------------------------------------------------------- fixtures

    private void configure(ReviewWorkflowStep... steps) {
        when(stepRepository.findByCompanyIdAndActiveTrueOrderByStepOrderAsc(COMPANY))
                .thenReturn(List.of(steps));
    }

    private ReviewWorkflowStep step(int order, WorkflowStepType type, boolean required) {
        ReviewWorkflowStep step = new ReviewWorkflowStep();
        step.setCompanyId(COMPANY);
        step.setStepOrder(order);
        step.setStepType(type);
        step.setName(switch (type) {
            case SELF_ASSESSMENT -> "Self assessment";
            case MANAGER_REVIEW -> "Manager review";
            case DEPARTMENT_HEAD_REVIEW -> "Department head review";
            case HR_REVIEW -> "HR review";
        });
        step.setRequired(required);
        step.setActive(true);
        return step;
    }
}
