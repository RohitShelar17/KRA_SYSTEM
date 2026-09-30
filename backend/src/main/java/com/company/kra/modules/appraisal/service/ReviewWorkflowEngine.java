package com.company.kra.modules.appraisal.service;

import com.company.kra.common.enums.AppraisalStep;
import com.company.kra.common.enums.WorkflowStepType;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.modules.appraisal.entity.ReviewWorkflowStep;
import com.company.kra.modules.appraisal.repository.ReviewWorkflowStepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Decides which step an appraisal is on and which comes next.
 *
 * <p>The flow is configuration, not code: an ordered list of active steps.
 * Deactivating the department-head step removes it from every appraisal in
 * flight, and an optional step can be skipped without leaving a hole. Keeping
 * that decision in one place means the service layer never has to reason about
 * step ordering itself.</p>
 */
@Component
@RequiredArgsConstructor
public class ReviewWorkflowEngine {

    private final ReviewWorkflowStepRepository stepRepository;

    @Transactional(readOnly = true)
    public List<ReviewWorkflowStep> activeSteps(Long companyId) {
        List<ReviewWorkflowStep> steps = stepRepository
                .findByCompanyIdAndActiveTrueOrderByStepOrderAsc(companyId);
        if (steps.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "No review workflow is configured. Set one up before opening a cycle.");
        }
        return steps;
    }

    /** Where a new appraisal starts. */
    @Transactional(readOnly = true)
    public AppraisalStep firstStep(Long companyId) {
        return AppraisalStep.from(activeSteps(companyId).get(0).getStepType());
    }

    /** The step after the current one, or COMPLETED when there is none. */
    @Transactional(readOnly = true)
    public AppraisalStep nextStep(Long companyId, AppraisalStep current) {
        if (current == AppraisalStep.COMPLETED) {
            return AppraisalStep.COMPLETED;
        }
        List<ReviewWorkflowStep> steps = activeSteps(companyId);

        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).getStepType() == current.asStepType()) {
                return index + 1 < steps.size()
                        ? AppraisalStep.from(steps.get(index + 1).getStepType())
                        : AppraisalStep.COMPLETED;
            }
        }

        // The current step was deactivated while this appraisal was sitting on
        // it. Fall back to the enum's stage order, which is the canonical
        // sequence, and move to the first configured step after it — so a
        // configuration change never strands an appraisal mid-flow.
        int stranded = current.asStepType().ordinal();
        return steps.stream()
                .filter(step -> step.getStepType().ordinal() > stranded)
                .findFirst()
                .map(step -> AppraisalStep.from(step.getStepType()))
                .orElse(AppraisalStep.COMPLETED);
    }

    @Transactional(readOnly = true)
    public Optional<ReviewWorkflowStep> step(Long companyId, WorkflowStepType type) {
        return activeSteps(companyId).stream()
                .filter(step -> step.getStepType() == type)
                .findFirst();
    }

    @Transactional(readOnly = true)
    public boolean isActive(Long companyId, WorkflowStepType type) {
        return step(companyId, type).isPresent();
    }

    /**
     * A step can only be skipped when the configuration says it is optional.
     * Skipping a required step would produce an appraisal nobody signed off.
     */
    @Transactional(readOnly = true)
    public void assertSkippable(Long companyId, AppraisalStep current) {
        WorkflowStepType type = current.asStepType();
        if (type == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This appraisal is already complete");
        }
        ReviewWorkflowStep step = step(companyId, type)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "That step is not part of the configured workflow"));
        if (step.isRequired()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "\"" + step.getName() + "\" is a required step and cannot be skipped");
        }
    }

    /** Position in the flow, used for progress display and ordering checks. */
    @Transactional(readOnly = true)
    public int positionOf(Long companyId, AppraisalStep step) {
        if (step == AppraisalStep.COMPLETED) {
            return activeSteps(companyId).size();
        }
        List<ReviewWorkflowStep> steps = activeSteps(companyId);
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).getStepType() == step.asStepType()) {
                return index;
            }
        }
        return 0;
    }

}
