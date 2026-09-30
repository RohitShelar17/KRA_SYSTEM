package com.company.kra.modules.appraisal.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.dto.PageResponse;
import com.company.kra.common.enums.AppraisalStatus;
import com.company.kra.common.enums.AppraisalStep;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.events.NotificationRaised;
import com.company.kra.common.enums.ReviewerRole;
import com.company.kra.common.enums.StepAction;
import com.company.kra.common.enums.SubmissionStatus;
import com.company.kra.common.enums.WorkflowStepType;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.appraisal.dto.AdvanceStepRequest;
import com.company.kra.modules.appraisal.dto.AppraisalDetailResponse;
import com.company.kra.modules.appraisal.dto.AppraisalSummaryResponse;
import com.company.kra.modules.appraisal.dto.ManagerReviewRequest;
import com.company.kra.modules.appraisal.dto.ManagerReviewResponse;
import com.company.kra.modules.appraisal.dto.SelfAssessmentRequest;
import com.company.kra.modules.appraisal.dto.SelfAssessmentResponse;
import com.company.kra.modules.appraisal.dto.StepActionResponse;
import com.company.kra.modules.appraisal.dto.WorkflowStepResponse;
import com.company.kra.modules.appraisal.entity.AppraisalRecord;
import com.company.kra.modules.appraisal.entity.ManagerReview;
import com.company.kra.modules.appraisal.entity.PerformanceCycle;
import com.company.kra.modules.appraisal.entity.ReviewStepAction;
import com.company.kra.modules.appraisal.entity.ReviewWorkflowStep;
import com.company.kra.modules.appraisal.entity.SelfAssessment;
import com.company.kra.modules.appraisal.repository.AppraisalRecordRepository;
import com.company.kra.modules.appraisal.repository.ManagerReviewRepository;
import com.company.kra.modules.appraisal.repository.ReviewStepActionRepository;
import com.company.kra.modules.appraisal.repository.SelfAssessmentRepository;
import com.company.kra.modules.organisation.service.DepartmentDirectory;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.security.AccessScopeResolver;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The appraisal itself: the self assessment, the reviews, and moving through
 * the workflow.
 *
 * <p>Who may do what is decided here, not in the UI. An employee writes their
 * own assessment and nobody else's; a reviewer rates people inside their access
 * scope and never themselves; and a step only advances when the work it asks
 * for has actually been submitted.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppraisalService {

    private final AppraisalRecordRepository appraisalRepository;
    private final SelfAssessmentRepository selfAssessmentRepository;
    private final ManagerReviewRepository reviewRepository;
    private final ReviewStepActionRepository actionRepository;
    private final EmployeeRepository employeeRepository;
    private final PerformanceCycleService cycleService;
    private final ReviewWorkflowEngine workflowEngine;
    private final AppraisalScoreLink scoreLink;
    private final AccessScopeResolver accessScopeResolver;
    private final AuditService auditService;
    private final DepartmentDirectory departmentDirectory;
    private final ApplicationEventPublisher events;

    // ----------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public List<AppraisalSummaryResponse> myAppraisals() {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long employeeId = requireEmployeeId(principal);
        return appraisalRepository.findByEmployeeIdOrderByIdDesc(employeeId).stream()
                .map(record -> toSummary(record, principal))
                .toList();
    }

    /** The reviewer's queue: appraisals sitting on a step they own. */
    @Transactional(readOnly = true)
    public PageResponse<AppraisalSummaryResponse> queue(Long cycleId, AppraisalStep step,
                                                        Pageable pageable) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AccessScopeResolver.Scope scope = accessScopeResolver.visibleEmployeeIds(principal);

        if (!scope.organisationWide() && scope.employeeIds().isEmpty()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(),
                    0, 0, true, true);
        }

        // Nobody reviews themselves, so the caller never appears in their own queue.
        Long exclude = principal.getEmployeeId() == null ? -1L : principal.getEmployeeId();

        Page<AppraisalRecord> page = appraisalRepository.search(principal.getCompanyId(),
                scope.organisationWide(), scope.organisationWide() ? Set.of(-1L) : scope.employeeIds(),
                cycleId, step, exclude, pageable);
        return PageResponse.from(page, record -> toSummary(record, principal));
    }

    @Transactional(readOnly = true)
    public AppraisalDetailResponse detail(Long appraisalId) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AppraisalRecord record = require(appraisalId, principal.getCompanyId());
        assertCanRead(record, principal);
        return toDetail(record, principal);
    }

    /** The caller's own appraisal for a cycle, creating nothing if it does not exist. */
    @Transactional(readOnly = true)
    public AppraisalDetailResponse myAppraisalForCycle(Long cycleId) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long employeeId = requireEmployeeId(principal);

        AppraisalRecord record = appraisalRepository.findByCycleIdAndEmployeeId(cycleId, employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Appraisal", cycleId));
        return toDetail(record, principal);
    }

    // ----------------------------------------------------- self assessment

    @Transactional
    public SelfAssessmentResponse saveSelfAssessment(Long appraisalId, SelfAssessmentRequest request,
                                                     boolean submit) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AppraisalRecord record = require(appraisalId, principal.getCompanyId());

        if (!record.getEmployeeId().equals(principal.getEmployeeId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "You can only write your own self assessment");
        }
        if (!workflowEngine.isActive(principal.getCompanyId(), WorkflowStepType.SELF_ASSESSMENT)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Self assessment is not part of the configured workflow");
        }

        PerformanceCycle cycle = record.getCycle();
        if (!cycle.selfAssessmentOpen(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    windowMessage(cycle, "Self assessment"));
        }

        SelfAssessment assessment = selfAssessmentRepository.findByAppraisalId(appraisalId)
                .orElseGet(() -> {
                    SelfAssessment fresh = new SelfAssessment();
                    fresh.setCompanyId(principal.getCompanyId());
                    fresh.setAppraisal(record);
                    fresh.setEmployeeId(record.getEmployeeId());
                    return fresh;
                });

        if (assessment.isSubmitted()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Your self assessment has been submitted and can no longer be edited");
        }

        assessment.setAchievements(request.achievements());
        assessment.setChallenges(request.challenges());
        assessment.setSkillsDeveloped(request.skillsDeveloped());
        assessment.setImprovementAreas(request.improvementAreas());
        assessment.setTrainingNeeded(request.trainingNeeded());
        assessment.setFutureGoals(request.futureGoals());
        assessment.setComments(request.comments());
        assessment.setSelfRating(request.selfRating());

        if (submit) {
            if (isBlank(request.achievements())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Describe what you achieved before submitting");
            }
            assessment.setStatus(SubmissionStatus.SUBMITTED);
            assessment.setSubmittedAt(LocalDateTime.now());
        }

        selfAssessmentRepository.save(assessment);

        if (submit) {
            recordAction(record, WorkflowStepType.SELF_ASSESSMENT.name(), StepAction.SUBMITTED,
                    record.getCurrentStep(), null, principal, null);
            if (record.getCurrentStep() == AppraisalStep.SELF_ASSESSMENT) {
                advanceTo(record, workflowEngine.nextStep(principal.getCompanyId(),
                        AppraisalStep.SELF_ASSESSMENT), principal, null);
            }
            cycleService.markInReview(cycle);
            auditService.record(AuditModule.APPRAISAL, AuditAction.CREATE, "SelfAssessment",
                    assessment.getId(), null, Map.of("appraisalId", String.valueOf(appraisalId)), null);
            log.info("Self assessment submitted for appraisal {}", appraisalId);
        }

        return toSelfAssessment(assessment, record);
    }

    // ------------------------------------------------------------- reviews

    @Transactional
    public ManagerReviewResponse saveReview(Long appraisalId, ManagerReviewRequest request,
                                            boolean submit) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AppraisalRecord record = require(appraisalId, principal.getCompanyId());

        if (record.getEmployeeId().equals(principal.getEmployeeId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "You cannot review your own appraisal");
        }
        accessScopeResolver.assertCanAccess(record.getEmployeeId());

        ReviewerRole role = ReviewerRole.forStep(record.getCurrentStep());
        if (role == null) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    record.getCurrentStep() == AppraisalStep.COMPLETED
                            ? "This appraisal is already complete"
                            : "This appraisal is waiting on the employee's self assessment");
        }

        PerformanceCycle cycle = record.getCycle();
        if (!cycle.reviewOpen(LocalDate.now())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    windowMessage(cycle, "Review"));
        }

        ManagerReview review = reviewRepository.findByAppraisalIdAndReviewerRole(appraisalId, role)
                .orElseGet(() -> {
                    ManagerReview fresh = new ManagerReview();
                    fresh.setCompanyId(principal.getCompanyId());
                    fresh.setAppraisal(record);
                    fresh.setEmployeeId(record.getEmployeeId());
                    fresh.setReviewerRole(role);
                    return fresh;
                });

        if (review.isSubmitted()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This review has been submitted and can no longer be edited");
        }

        review.setReviewerEmployeeId(principal.getEmployeeId());
        review.setReviewerName(principal.getDisplayName());
        review.setQualityRating(request.qualityRating());
        review.setProductivityRating(request.productivityRating());
        review.setTimelinessRating(request.timelinessRating());
        review.setTeamworkRating(request.teamworkRating());
        review.setCommunicationRating(request.communicationRating());
        review.setInitiativeRating(request.initiativeRating());
        review.setProblemSolvingRating(request.problemSolvingRating());
        review.setDisciplineRating(request.disciplineRating());
        review.setKraRating(request.kraRating());
        review.setKpiRating(request.kpiRating());
        review.setOverallRating(request.overallRating());
        review.setStrengths(request.strengths());
        review.setImprovements(request.improvements());
        review.setComments(request.comments());

        if (submit) {
            // The engine reads the quality and overall ratings, so a submitted
            // review without them would leave the score unmeasurable.
            if (request.qualityRating() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "A quality rating is required: the score reads it directly");
            }
            if (review.overallPercent() == null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Give an overall rating, or rate enough competencies for one to be derived");
            }
            if (isBlank(request.comments()) && isBlank(request.strengths())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Write something the employee can act on, not just numbers");
            }
            review.setStatus(SubmissionStatus.SUBMITTED);
            review.setSubmittedAt(LocalDateTime.now());
        }

        reviewRepository.save(review);

        if (submit) {
            recordAction(record, record.getCurrentStep().name(), StepAction.SUBMITTED,
                    record.getCurrentStep(), null, principal, null);
            advanceTo(record, workflowEngine.nextStep(principal.getCompanyId(),
                    record.getCurrentStep()), principal, null);

            auditService.record(AuditModule.APPRAISAL, AuditAction.CREATE, "ManagerReview",
                    review.getId(), null, Map.of("appraisalId", String.valueOf(appraisalId),
                            "role", role.name(),
                            "overall", String.valueOf(review.getOverallRating())), null);
            log.info("{} review submitted for appraisal {}", role, appraisalId);
        }

        return toReview(review, record);
    }

    // ------------------------------------------------------------ workflow

    /** Move an appraisal on without a review, when the workflow allows it. */
    @Transactional
    public AppraisalDetailResponse advance(Long appraisalId, AdvanceStepRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AppraisalRecord record = require(appraisalId, principal.getCompanyId());
        accessScopeResolver.assertCanAccess(record.getEmployeeId());

        if (record.isComplete()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This appraisal is already complete");
        }

        if (request.skip()) {
            workflowEngine.assertSkippable(principal.getCompanyId(), record.getCurrentStep());
            recordAction(record, record.getCurrentStep().name(), StepAction.SKIPPED,
                    record.getCurrentStep(), null, principal, request.comments());
        } else {
            assertStepWorkDone(record, principal);
            recordAction(record, record.getCurrentStep().name(), StepAction.APPROVED,
                    record.getCurrentStep(), null, principal, request.comments());
        }

        advanceTo(record, workflowEngine.nextStep(principal.getCompanyId(), record.getCurrentStep()),
                principal, request.comments());
        return toDetail(record, principal);
    }

    /** Send an appraisal back a step so the earlier work can be redone. */
    @Transactional
    public AppraisalDetailResponse returnStep(Long appraisalId, String comments) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        AppraisalRecord record = require(appraisalId, principal.getCompanyId());
        accessScopeResolver.assertCanAccess(record.getEmployeeId());

        List<ReviewWorkflowStep> steps = workflowEngine.activeSteps(principal.getCompanyId());
        int position = workflowEngine.positionOf(principal.getCompanyId(), record.getCurrentStep());
        if (position == 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This appraisal is on the first step; there is nothing to send it back to");
        }

        AppraisalStep previous = AppraisalStep.from(steps.get(position - 1).getStepType());
        AppraisalStep from = record.getCurrentStep();

        // Reopen whatever was submitted at the step being returned to, so the
        // author can actually change it.
        reopenSubmission(record, previous);

        recordAction(record, from.name(), StepAction.RETURNED, from, previous, principal, comments);
        record.setCurrentStep(previous);
        record.setStatus(AppraisalStatus.IN_PROGRESS);
        appraisalRepository.save(record);

        auditService.record(AuditModule.APPRAISAL, AuditAction.REJECT, "AppraisalRecord",
                appraisalId, Map.of("step", from.name()), Map.of("step", previous.name()), comments);

        log.info("Appraisal {} returned from {} to {}", appraisalId, from, previous);

        events.publishEvent(NotificationRaised
                .to(principal.getCompanyId(), "APPRAISAL_RETURNED")
                .recipients(ownersOf(record, previous))
                .about("AppraisalRecord", appraisalId)
                .with("actorName", principal.getDisplayName())
                .with("periodLabel", record.getCycle().periodLabel())
                .with("stepName", stepWording(principal.getCompanyId(), previous))
                .with("reason", comments)
                .build());

        return toDetail(record, principal);
    }

    // ------------------------------------------------------------ internals

    private void advanceTo(AppraisalRecord record, AppraisalStep next, AppUserPrincipal principal,
                           String comments) {
        AppraisalStep from = record.getCurrentStep();
        record.setCurrentStep(next);

        if (next == AppraisalStep.COMPLETED) {
            record.setStatus(AppraisalStatus.COMPLETED);
            record.setCompletedAt(LocalDateTime.now());
            // Freeze the figure the appraisal concluded on, so a later
            // recalculation cannot rewrite what was signed off.
            scoreLink.captureFinalScore(record);
            recordAction(record, "COMPLETED", StepAction.COMPLETED, from, next, principal, comments);
            auditService.record(AuditModule.APPRAISAL, AuditAction.FINALIZE, "AppraisalRecord",
                    record.getId(), Map.of("step", from.name()),
                    Map.of("step", next.name(), "finalScore",
                            String.valueOf(record.getFinalScore())), comments);
            log.info("Appraisal {} completed with final score {}", record.getId(),
                    record.getFinalScore());
        } else {
            record.setStatus(AppraisalStatus.IN_PROGRESS);
        }
        appraisalRepository.save(record);

        if (next == AppraisalStep.COMPLETED) {
            events.publishEvent(NotificationRaised
                    .to(record.getCompanyId(), "APPRAISAL_COMPLETED")
                    .recipient(record.getEmployeeId())
                    .about("AppraisalRecord", record.getId())
                    .with("actorName", principal.getDisplayName())
                    .with("periodLabel", record.getCycle().periodLabel())
                    .build());
        } else {
            // Whoever owns the new step is the person who needs to act.
            events.publishEvent(NotificationRaised
                    .to(record.getCompanyId(), "APPRAISAL_STEP_READY")
                    .recipients(ownersOf(record, next))
                    .about("AppraisalRecord", record.getId())
                    .with("employeeName", employeeRepository.findById(record.getEmployeeId())
                            .map(Employee::fullName).orElse("An employee"))
                    .with("periodLabel", record.getCycle().periodLabel())
                    .with("stepName", stepWording(record.getCompanyId(), next))
                    .build());
        }
    }

    /**
     * Who is expected to act on a step. The reporting manager and the
     * department head are individuals the system can name; HR review is owned
     * by a permission rather than a person, so it is left to the queue rather
     * than guessing at a recipient.
     */
    private Set<Long> ownersOf(AppraisalRecord record, AppraisalStep step) {
        return switch (step) {
            case SELF_ASSESSMENT -> Set.of(record.getEmployeeId());
            case MANAGER_REVIEW -> record.getManagerId() == null
                    ? Set.of() : Set.of(record.getManagerId());
            case DEPARTMENT_HEAD_REVIEW -> employeeRepository.findById(record.getEmployeeId())
                    .map(Employee::getDepartment)
                    .map(department -> departmentDirectory.headOf(department.getId()))
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .filter(head -> !head.equals(record.getEmployeeId()))
                    .map(Set::of)
                    .orElse(Set.of());
            case HR_REVIEW, COMPLETED -> Set.of();
        };
    }

    /** The organisation's own name for the step, falling back to the enum. */
    private String stepWording(Long companyId, AppraisalStep step) {
        if (step == AppraisalStep.COMPLETED) {
            return "sign-off";
        }
        return workflowEngine.step(companyId, step.asStepType())
                .map(ReviewWorkflowStep::getName)
                .orElse(step.name().toLowerCase().replace('_', ' '));
    }

    /** A step only advances when the thing it asks for has been submitted. */
    private void assertStepWorkDone(AppraisalRecord record, AppUserPrincipal principal) {
        switch (record.getCurrentStep()) {
            case SELF_ASSESSMENT -> {
                boolean submitted = selfAssessmentRepository.findByAppraisalId(record.getId())
                        .map(SelfAssessment::isSubmitted)
                        .orElse(false);
                if (!submitted) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "The self assessment has not been submitted yet. Skip the step if the "
                                    + "employee is not going to complete it.");
                }
            }
            case MANAGER_REVIEW, DEPARTMENT_HEAD_REVIEW, HR_REVIEW -> {
                ReviewerRole role = ReviewerRole.forStep(record.getCurrentStep());
                boolean submitted = reviewRepository
                        .findByAppraisalIdAndReviewerRole(record.getId(), role)
                        .map(ManagerReview::isSubmitted)
                        .orElse(false);
                if (!submitted) {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "The " + role.name().toLowerCase().replace('_', ' ')
                                    + " review has not been submitted yet");
                }
            }
            default -> {
            }
        }
    }

    private void reopenSubmission(AppraisalRecord record, AppraisalStep step) {
        if (step == AppraisalStep.SELF_ASSESSMENT) {
            selfAssessmentRepository.findByAppraisalId(record.getId()).ifPresent(assessment -> {
                assessment.setStatus(SubmissionStatus.DRAFT);
                assessment.setSubmittedAt(null);
                selfAssessmentRepository.save(assessment);
            });
            return;
        }
        ReviewerRole role = ReviewerRole.forStep(step);
        if (role != null) {
            reviewRepository.findByAppraisalIdAndReviewerRole(record.getId(), role)
                    .ifPresent(review -> {
                        review.setStatus(SubmissionStatus.DRAFT);
                        review.setSubmittedAt(null);
                        reviewRepository.save(review);
                    });
        }
    }

    private void recordAction(AppraisalRecord record, String stepType, StepAction action,
                              AppraisalStep from, AppraisalStep to, AppUserPrincipal principal,
                              String comments) {
        ReviewStepAction row = new ReviewStepAction();
        row.setCompanyId(record.getCompanyId());
        row.setAppraisalId(record.getId());
        row.setStepType(stepType);
        row.setAction(action);
        row.setFromStep(from == null ? null : from.name());
        row.setToStep(to == null ? null : to.name());
        row.setActorEmployeeId(principal.getEmployeeId());
        row.setActorName(principal.getDisplayName());
        row.setComments(comments);
        actionRepository.save(row);
    }

    private void assertCanRead(AppraisalRecord record, AppUserPrincipal principal) {
        if (record.getEmployeeId().equals(principal.getEmployeeId())) {
            return;
        }
        accessScopeResolver.assertCanAccess(record.getEmployeeId());
    }

    private AppraisalRecord require(Long id, Long companyId) {
        return appraisalRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Appraisal", id));
    }

    private Long requireEmployeeId(AppUserPrincipal principal) {
        if (principal.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "This account is not linked to an employee record");
        }
        return principal.getEmployeeId();
    }

    private String windowMessage(PerformanceCycle cycle, String label) {
        if (!cycle.getStatus().acceptsInput()) {
            return "The " + cycle.getName() + " cycle is " + cycle.getStatus().name().toLowerCase()
                    + " and is not accepting input";
        }
        return label + " for " + cycle.getName() + " is outside its window";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ----------------------------------------------------------- projections

    private AppraisalSummaryResponse toSummary(AppraisalRecord record, AppUserPrincipal principal) {
        Employee employee = employeeRepository.findById(record.getEmployeeId()).orElse(null);
        boolean selfDone = selfAssessmentRepository.findByAppraisalId(record.getId())
                .map(SelfAssessment::isSubmitted).orElse(false);
        boolean managerDone = reviewRepository
                .findByAppraisalIdAndReviewerRole(record.getId(), ReviewerRole.MANAGER)
                .map(ManagerReview::isSubmitted).orElse(false);

        boolean waitingOnMe = record.getCurrentStep() == AppraisalStep.SELF_ASSESSMENT
                ? record.getEmployeeId().equals(principal.getEmployeeId())
                : !record.getEmployeeId().equals(principal.getEmployeeId()) && !record.isComplete();

        String stepName = record.isComplete() ? "Completed"
                : workflowEngine.step(record.getCompanyId(), record.getCurrentStep().asStepType())
                .map(ReviewWorkflowStep::getName)
                .orElse(record.getCurrentStep().name());

        return new AppraisalSummaryResponse(record.getId(), record.getCycle().getId(),
                record.getCycle().getName(), record.getCycle().periodLabel(), record.getEmployeeId(),
                employee == null ? null : employee.fullName(),
                employee == null ? null : employee.getEmployeeCode(),
                employee == null || employee.getDesignation() == null
                        ? null : employee.getDesignation().getName(),
                record.getCurrentStep(), stepName, record.getStatus(), record.getComputedScore(),
                record.getFinalScore(), selfDone, managerDone, waitingOnMe);
    }

    private AppraisalDetailResponse toDetail(AppraisalRecord record, AppUserPrincipal principal) {
        Employee employee = employeeRepository.findById(record.getEmployeeId()).orElse(null);
        String managerName = record.getManagerId() == null ? null
                : employeeRepository.findById(record.getManagerId()).map(Employee::fullName).orElse(null);

        List<WorkflowStepResponse> workflow = workflowEngine.activeSteps(record.getCompanyId()).stream()
                .map(step -> new WorkflowStepResponse(step.getId(), step.getStepOrder(),
                        step.getStepType(), step.getName(), step.getDescription(), step.isRequired(),
                        step.getSlaDays(), step.isActive()))
                .toList();

        SelfAssessmentResponse assessment = selfAssessmentRepository.findByAppraisalId(record.getId())
                .map(row -> toSelfAssessment(row, record))
                .orElse(null);

        List<ManagerReviewResponse> reviews = reviewRepository
                .findByAppraisalIdOrderByIdAsc(record.getId()).stream()
                .map(row -> toReview(row, record))
                .toList();

        List<StepActionResponse> history = actionRepository
                .findByAppraisalIdOrderByCreatedAtAsc(record.getId()).stream()
                .map(row -> new StepActionResponse(row.getId(), row.getStepType(), row.getAction(),
                        row.getFromStep(), row.getToStep(), row.getActorName(), row.getComments(),
                        row.getCreatedAt()))
                .toList();

        boolean isOwn = record.getEmployeeId().equals(principal.getEmployeeId());
        boolean canSelfAssess = isOwn
                && record.getCurrentStep() == AppraisalStep.SELF_ASSESSMENT
                && record.getCycle().selfAssessmentOpen(LocalDate.now())
                && (assessment == null || assessment.status() != SubmissionStatus.SUBMITTED);

        boolean canReview = !isOwn
                && ReviewerRole.forStep(record.getCurrentStep()) != null
                && record.getCycle().reviewOpen(LocalDate.now())
                && principal.hasPermission("PERFORMANCE_REVIEW");

        var band = scoreLink.bandFor(record);

        return new AppraisalDetailResponse(record.getId(), record.getCycle().getId(),
                record.getCycle().getName(), record.getCycle().periodLabel(),
                record.getCycle().getPeriodStart(), record.getCycle().getPeriodEnd(),
                record.getEmployeeId(), employee == null ? null : employee.fullName(),
                employee == null ? null : employee.getEmployeeCode(),
                employee == null || employee.getDesignation() == null
                        ? null : employee.getDesignation().getName(),
                managerName, record.getCurrentStep(), record.getStatus(),
                workflowEngine.positionOf(record.getCompanyId(), record.getCurrentStep()),
                workflow.size(), workflow, assessment, reviews, history,
                record.getComputedScore(), record.getFinalScore(),
                band.map(row -> row.bandName()).orElse(null),
                band.map(row -> row.bandColour()).orElse(null),
                record.getCompletedAt(), canSelfAssess, canReview,
                !record.isComplete() && principal.hasPermission("PERFORMANCE_REVIEW"));
    }

    private SelfAssessmentResponse toSelfAssessment(SelfAssessment assessment, AppraisalRecord record) {
        Employee employee = employeeRepository.findById(assessment.getEmployeeId()).orElse(null);
        return new SelfAssessmentResponse(assessment.getId(), record.getId(),
                assessment.getEmployeeId(), employee == null ? null : employee.fullName(),
                assessment.getAchievements(), assessment.getChallenges(),
                assessment.getSkillsDeveloped(), assessment.getImprovementAreas(),
                assessment.getTrainingNeeded(), assessment.getFutureGoals(), assessment.getComments(),
                assessment.getSelfRating(), assessment.getStatus(), !assessment.isSubmitted(),
                assessment.getSubmittedAt());
    }

    private ManagerReviewResponse toReview(ManagerReview review, AppraisalRecord record) {
        Employee employee = employeeRepository.findById(review.getEmployeeId()).orElse(null);

        Map<String, BigDecimal> ratings = new LinkedHashMap<>();
        putRating(ratings, "quality", review.getQualityRating());
        putRating(ratings, "productivity", review.getProductivityRating());
        putRating(ratings, "timeliness", review.getTimelinessRating());
        putRating(ratings, "teamwork", review.getTeamworkRating());
        putRating(ratings, "communication", review.getCommunicationRating());
        putRating(ratings, "initiative", review.getInitiativeRating());
        putRating(ratings, "problemSolving", review.getProblemSolvingRating());
        putRating(ratings, "discipline", review.getDisciplineRating());
        putRating(ratings, "kra", review.getKraRating());
        putRating(ratings, "kpi", review.getKpiRating());

        return new ManagerReviewResponse(review.getId(), record.getId(), review.getEmployeeId(),
                employee == null ? null : employee.fullName(), review.getReviewerEmployeeId(),
                review.getReviewerName(), review.getReviewerRole(), ratings, review.getOverallRating(),
                review.getStrengths(), review.getImprovements(), review.getComments(),
                review.getStatus(), !review.isSubmitted(), review.getSubmittedAt());
    }

    private void putRating(Map<String, BigDecimal> target, String key, BigDecimal value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
