package com.company.kra.modules.appraisal.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.enums.WorkflowStepType;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.modules.appraisal.dto.WorkflowStepRequest;
import com.company.kra.modules.appraisal.dto.WorkflowStepResponse;
import com.company.kra.modules.appraisal.entity.ReviewWorkflowStep;
import com.company.kra.modules.appraisal.repository.ReviewWorkflowStepRepository;
import com.company.kra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The review flow as configuration.
 *
 * <p>Steps are saved as an ordered set, in the order they were sent. Two rules
 * hold: the flow must keep at least one active step, and it must end with
 * somebody signing off — a flow of nothing but a self assessment would let
 * people rate themselves into a final rating.</p>
 */
@Service
@RequiredArgsConstructor
public class WorkflowConfigService {

    private final ReviewWorkflowStepRepository stepRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<WorkflowStepResponse> list() {
        Long companyId = SecurityUtils.requireCompanyId();
        return stepRepository.findByCompanyIdOrderByStepOrderAsc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<WorkflowStepResponse> replaceAll(List<WorkflowStepRequest> requests) {
        Long companyId = SecurityUtils.requireCompanyId();
        validate(requests);

        List<ReviewWorkflowStep> existing = stepRepository.findByCompanyIdOrderByStepOrderAsc(companyId);
        String before = describe(existing);

        Map<WorkflowStepType, ReviewWorkflowStep> byType = new java.util.EnumMap<>(WorkflowStepType.class);
        existing.forEach(step -> byType.put(step.getStepType(), step));

        List<ReviewWorkflowStep> saved = new ArrayList<>();
        int order = 10;
        for (WorkflowStepRequest request : requests) {
            // Updated in place where the step already exists, so appraisals in
            // flight keep pointing at a row that is still there.
            ReviewWorkflowStep step = byType.getOrDefault(request.stepType(), new ReviewWorkflowStep());
            step.setCompanyId(companyId);
            step.setStepType(request.stepType());
            step.setName(request.name().trim());
            step.setDescription(request.description());
            step.setRequired(request.required());
            step.setActive(request.active());
            step.setSlaDays(request.slaDays());
            step.setStepOrder(order);
            saved.add(stepRepository.save(step));
            order += 10;
        }

        // Anything not sent is deactivated rather than deleted.
        existing.stream()
                .filter(step -> requests.stream().noneMatch(r -> r.stepType() == step.getStepType()))
                .forEach(step -> {
                    step.setActive(false);
                    stepRepository.save(step);
                    saved.add(step);
                });

        List<WorkflowStepResponse> responses = saved.stream()
                .sorted(java.util.Comparator.comparingInt(ReviewWorkflowStep::getStepOrder))
                .map(this::toResponse)
                .toList();

        auditService.record(AuditModule.APPRAISAL, AuditAction.UPDATE, "ReviewWorkflowStep", null,
                Map.of("workflow", before), Map.of("workflow", describe(saved)), null);
        return responses;
    }

    private void validate(List<WorkflowStepRequest> requests) {
        List<WorkflowStepRequest> active = requests.stream()
                .filter(WorkflowStepRequest::active)
                .toList();

        if (active.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "The workflow needs at least one active step");
        }

        Set<WorkflowStepType> seen = new HashSet<>();
        for (WorkflowStepRequest request : requests) {
            if (!seen.add(request.stepType())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        request.stepType().name() + " appears twice");
            }
        }

        boolean hasReviewer = active.stream()
                .anyMatch(step -> step.stepType() != WorkflowStepType.SELF_ASSESSMENT);
        if (!hasReviewer) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "A workflow of only self assessment would let people rate themselves into a "
                            + "final rating. Add at least one review step.");
        }

        WorkflowStepRequest last = active.get(active.size() - 1);
        if (!last.required()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "The last step signs the appraisal off, so it cannot be optional");
        }
    }

    private String describe(List<ReviewWorkflowStep> steps) {
        StringBuilder builder = new StringBuilder();
        steps.stream()
                .sorted(java.util.Comparator.comparingInt(ReviewWorkflowStep::getStepOrder))
                .forEach(step -> builder.append(step.getStepType().name())
                        .append(step.isActive() ? (step.isRequired() ? "(required)" : "(optional)")
                                : "(off)")
                        .append(' '));
        return builder.toString().trim();
    }

    private WorkflowStepResponse toResponse(ReviewWorkflowStep step) {
        return new WorkflowStepResponse(step.getId(), step.getStepOrder(), step.getStepType(),
                step.getName(), step.getDescription(), step.isRequired(), step.getSlaDays(),
                step.isActive());
    }
}
