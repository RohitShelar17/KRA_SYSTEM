package com.company.kra.modules.appraisal.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.AppraisalStatus;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.events.NotificationRaised;
import com.company.kra.common.enums.AppraisalStep;
import com.company.kra.common.enums.CycleStatus;
import com.company.kra.common.enums.PeriodType;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.DuplicateResourceException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.appraisal.dto.CycleLaunchResponse;
import com.company.kra.modules.appraisal.dto.CycleRequest;
import com.company.kra.modules.appraisal.dto.CycleResponse;
import com.company.kra.modules.appraisal.entity.AppraisalRecord;
import com.company.kra.modules.appraisal.entity.PerformanceCycle;
import com.company.kra.modules.appraisal.repository.AppraisalRecordRepository;
import com.company.kra.modules.appraisal.repository.PerformanceCycleRepository;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Appraisal cycles: the round, its windows, and the roster of people in it.
 *
 * <p>Opening a cycle is what creates the appraisal records. Each one captures
 * the reporting manager as it stands at that moment, so a reassignment
 * mid-cycle cannot leave a review without an owner.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceCycleService {

    private final PerformanceCycleRepository cycleRepository;
    private final AppraisalRecordRepository appraisalRepository;
    private final EmployeeRepository employeeRepository;
    private final ReviewWorkflowEngine workflowEngine;
    private final AuditService auditService;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<CycleResponse> list() {
        Long companyId = SecurityUtils.requireCompanyId();
        return cycleRepository.findByCompanyIdAndActiveTrueOrderByPeriodStartDesc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    /** Cycles currently accepting input, for the employee and manager screens. */
    @Transactional(readOnly = true)
    public List<CycleResponse> active() {
        Long companyId = SecurityUtils.requireCompanyId();
        return cycleRepository.findByCompanyIdAndStatusInAndActiveTrueOrderByPeriodStartDesc(
                        companyId, List.of(CycleStatus.OPEN, CycleStatus.IN_REVIEW)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CycleResponse get(Long id) {
        return toResponse(require(id, SecurityUtils.requireCompanyId()));
    }

    @Transactional
    public CycleResponse create(CycleRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        PeriodType type = request.scopeType();
        short year = request.periodYear().shortValue();
        byte index = request.periodIndex().byteValue();

        if (index < 1 || index > type.periodsPerYear()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Period " + index + " is not valid for a " + type.name().toLowerCase() + " cycle");
        }
        if (cycleRepository.existsByCompanyIdAndScopeTypeAndPeriodYearAndPeriodIndex(
                companyId, type, year, index)) {
            throw new DuplicateResourceException("Cycle", "period", type.label(year, index));
        }

        PerformanceCycle cycle = new PerformanceCycle();
        cycle.setCompanyId(companyId);
        cycle.setScopeType(type);
        cycle.setPeriodYear(year);
        cycle.setPeriodIndex(index);
        cycle.setPeriodStart(type.startOf(year, index));
        cycle.setPeriodEnd(type.endOf(year, index));
        apply(cycle, request);
        cycle.setStatus(CycleStatus.DRAFT);

        PerformanceCycle saved = cycleRepository.save(cycle);
        auditService.record(AuditModule.APPRAISAL, AuditAction.CREATE, "PerformanceCycle",
                saved.getId(), null, Map.of("name", saved.getName(),
                        "period", saved.periodLabel()), null);
        return toResponse(saved);
    }

    @Transactional
    public CycleResponse update(Long id, CycleRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        PerformanceCycle cycle = require(id, companyId);

        if (cycle.getStatus() == CycleStatus.CLOSED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "A closed cycle cannot be edited");
        }

        Map<String, String> before = Map.of("name", cycle.getName(),
                "selfAssessmentCloses", String.valueOf(cycle.getSelfAssessmentCloses()),
                "reviewCloses", String.valueOf(cycle.getReviewCloses()));

        apply(cycle, request);
        cycleRepository.save(cycle);

        auditService.record(AuditModule.APPRAISAL, AuditAction.UPDATE, "PerformanceCycle", id,
                before, Map.of("name", cycle.getName(),
                        "selfAssessmentCloses", String.valueOf(cycle.getSelfAssessmentCloses()),
                        "reviewCloses", String.valueOf(cycle.getReviewCloses())), null);
        return toResponse(cycle);
    }

    /**
     * Opens the cycle and enrols the roster. Running it again is safe: anyone
     * already enrolled is left alone, so a late joiner can be added without
     * disturbing appraisals in flight.
     */
    @Transactional
    public CycleLaunchResponse open(Long id) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        PerformanceCycle cycle = require(id, principal.getCompanyId());

        if (cycle.getStatus() == CycleStatus.CLOSED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This cycle is closed. Create a new one for a fresh round.");
        }

        AppraisalStep firstStep = workflowEngine.firstStep(principal.getCompanyId());
        List<Employee> employees = employeeRepository
                .findByCompanyIdAndActiveTrueOrderByFirstNameAsc(principal.getCompanyId());

        int created = 0;
        int existing = 0;
        List<String> messages = new ArrayList<>();
        Set<Long> enrolled = new LinkedHashSet<>();

        for (Employee employee : employees) {
            if (appraisalRepository.findByCycleIdAndEmployeeId(cycle.getId(), employee.getId())
                    .isPresent()) {
                existing++;
                continue;
            }
            AppraisalRecord record = new AppraisalRecord();
            record.setCompanyId(principal.getCompanyId());
            record.setCycle(cycle);
            record.setEmployeeId(employee.getId());
            // Captured now, so a reassignment later cannot orphan this review.
            record.setManagerId(employee.getReportingManager() == null
                    ? null : employee.getReportingManager().getId());
            record.setCurrentStep(firstStep);
            record.setStatus(AppraisalStatus.NOT_STARTED);
            appraisalRepository.save(record);
            created++;
            enrolled.add(employee.getId());

            if (record.getManagerId() == null) {
                messages.add(employee.fullName() + " has no reporting manager; their review will "
                        + "need to be handled by HR.");
            }
        }

        if (cycle.getStatus() == CycleStatus.DRAFT) {
            cycle.setStatus(CycleStatus.OPEN);
            cycle.setOpenedAt(LocalDateTime.now());
            cycleRepository.save(cycle);
        }

        auditService.record(AuditModule.APPRAISAL, AuditAction.UPDATE, "PerformanceCycle", id,
                Map.of("status", CycleStatus.DRAFT.name()),
                Map.of("status", cycle.getStatus().name(), "participants", String.valueOf(created)),
                null);

        log.info("Cycle {} opened with {} participants ({} already enrolled)", cycle.getName(),
                created, existing);

        // Only the people whose appraisal was just created are told; re-opening
        // a cycle must not re-announce it to everybody who already has one.
        events.publishEvent(NotificationRaised
                .to(principal.getCompanyId(), "CYCLE_OPENED")
                .recipients(enrolled)
                .about("PerformanceCycle", cycle.getId())
                .with("cycleName", cycle.getName())
                .with("periodLabel", cycle.periodLabel())
                .with("windowSuffix", cycle.getSelfAssessmentCloses() == null
                        ? "" : " until " + cycle.getSelfAssessmentCloses())
                .build());
        return new CycleLaunchResponse(cycle.getId(), cycle.getName(), created, existing,
                messages.stream().limit(10).toList());
    }

    @Transactional
    public CycleResponse close(Long id) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        PerformanceCycle cycle = require(id, principal.getCompanyId());

        if (cycle.getStatus() == CycleStatus.CLOSED) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This cycle is already closed");
        }

        long total = appraisalRepository.countByCycleId(id);
        long complete = appraisalRepository.countByCycleIdAndCurrentStep(id, AppraisalStep.COMPLETED);
        if (total > 0 && complete < total) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    (total - complete) + " of " + total + " appraisals are still in progress. "
                            + "Finish or skip them before closing the cycle.");
        }

        cycle.setStatus(CycleStatus.CLOSED);
        cycle.setClosedAt(LocalDateTime.now());
        cycle.setClosedBy(principal.getEmployeeId());
        cycleRepository.save(cycle);

        auditService.record(AuditModule.APPRAISAL, AuditAction.FINALIZE, "PerformanceCycle", id,
                Map.of("status", CycleStatus.IN_REVIEW.name()),
                Map.of("status", CycleStatus.CLOSED.name()), null);

        log.info("Cycle {} closed with {} completed appraisals", cycle.getName(), complete);
        return toResponse(cycle);
    }

    /** Used by the appraisal service, which must not touch this repository. */
    @Transactional(readOnly = true)
    public PerformanceCycle require(Long id, Long companyId) {
        return cycleRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Cycle", id));
    }

    @Transactional
    public void markInReview(PerformanceCycle cycle) {
        if (cycle.getStatus() == CycleStatus.OPEN) {
            cycle.setStatus(CycleStatus.IN_REVIEW);
            cycleRepository.save(cycle);
        }
    }

    // ------------------------------------------------------------ internals

    private void apply(PerformanceCycle cycle, CycleRequest request) {
        cycle.setName(request.name().trim());
        cycle.setDescription(request.description());
        cycle.setSelfAssessmentOpens(request.selfAssessmentOpens());
        cycle.setSelfAssessmentCloses(request.selfAssessmentCloses());
        cycle.setReviewOpens(request.reviewOpens());
        cycle.setReviewCloses(request.reviewCloses());

        validateWindow(cycle.getSelfAssessmentOpens(), cycle.getSelfAssessmentCloses(),
                "self assessment");
        validateWindow(cycle.getReviewOpens(), cycle.getReviewCloses(), "review");
    }

    private void validateWindow(LocalDate opens, LocalDate closes, String label) {
        if (opens != null && closes != null && closes.isBefore(opens)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "The " + label + " window closes before it opens");
        }
    }

    private CycleResponse toResponse(PerformanceCycle cycle) {
        long participants = appraisalRepository.countByCycleId(cycle.getId());
        long completed = appraisalRepository.countByCycleIdAndCurrentStep(cycle.getId(),
                AppraisalStep.COMPLETED);
        double percent = participants == 0 ? 0
                : Math.round(completed * 1000.0 / participants) / 10.0;
        LocalDate today = LocalDate.now();

        return new CycleResponse(cycle.getId(), cycle.getName(), cycle.getDescription(),
                cycle.getScopeType(), cycle.getPeriodYear(), cycle.getPeriodIndex(),
                cycle.periodLabel(), cycle.getPeriodStart(), cycle.getPeriodEnd(),
                cycle.getSelfAssessmentOpens(), cycle.getSelfAssessmentCloses(),
                cycle.getReviewOpens(), cycle.getReviewCloses(), cycle.getStatus(),
                cycle.selfAssessmentOpen(today), cycle.reviewOpen(today),
                cycle.getOpenedAt(), cycle.getClosedAt(), participants, completed, percent);
    }
}
