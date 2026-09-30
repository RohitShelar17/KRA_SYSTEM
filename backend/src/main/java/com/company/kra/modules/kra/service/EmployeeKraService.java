package com.company.kra.modules.kra.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.dto.OptionDto;
import com.company.kra.common.enums.ActualSource;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.enums.KraStatus;
import com.company.kra.common.enums.PeriodType;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.kra.dto.AssignKraRequest;
import com.company.kra.modules.kra.dto.KpiActualRequest;
import com.company.kra.modules.kra.dto.KpiHistoryResponse;
import com.company.kra.modules.kra.dto.KpiProgressResponse;
import com.company.kra.modules.kra.dto.KpiTargetRequest;
import com.company.kra.modules.kra.dto.KraProgressResponse;
import com.company.kra.modules.kra.dto.KraScorecardResponse;
import com.company.kra.modules.kra.dto.WeightageUpdateRequest;
import com.company.kra.modules.kra.entity.EmployeeKpi;
import com.company.kra.modules.kra.entity.EmployeeKra;
import com.company.kra.modules.kra.entity.KpiActual;
import com.company.kra.modules.kra.entity.KpiTarget;
import com.company.kra.modules.kra.entity.KpiTemplateItem;
import com.company.kra.modules.kra.entity.KraTemplate;
import com.company.kra.modules.kra.entity.KraTemplateItem;
import com.company.kra.modules.kra.repository.EmployeeKpiRepository;
import com.company.kra.modules.kra.repository.EmployeeKraRepository;
import com.company.kra.modules.kra.repository.KpiActualRepository;
import com.company.kra.modules.kra.repository.KpiTargetRepository;
import com.company.kra.security.AccessScopeResolver;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Assigning KRAs to people, and reporting how they are doing against them.
 *
 * <p>Assignment copies the template rather than pointing at it, so a template
 * edited next month cannot rewrite what somebody was measured on last month.
 * Achievement is computed on read from targets and actuals — Phase 4 is what
 * freezes a score into a snapshot.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeKraService {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final EmployeeKraRepository kraRepository;
    private final EmployeeKpiRepository kpiRepository;
    private final KpiTargetRepository targetRepository;
    private final KpiActualRepository actualRepository;
    private final EmployeeRepository employeeRepository;
    private final KraTemplateService templateService;
    private final AchievementCalculator calculator;
    private final KraProgressReader progressReader;
    private final AccessScopeResolver accessScopeResolver;
    private final AuditService auditService;

    // ------------------------------------------------------------ scorecard

    @Transactional(readOnly = true)
    public KraScorecardResponse scorecard(Long employeeId, PeriodType periodType, Integer year,
                                          Integer index) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long target = employeeId == null ? requireEmployeeId(principal) : employeeId;
        if (!target.equals(principal.getEmployeeId())) {
            accessScopeResolver.assertCanAccess(target);
        }

        Employee employee = employeeRepository.findByIdAndCompanyId(target, principal.getCompanyId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee", target));

        PeriodType type = periodType == null ? PeriodType.QUARTERLY : periodType;
        int periodYear = year == null ? LocalDate.now().getYear() : year;
        int periodIndex = index == null ? currentIndex(type) : index;

        List<EmployeeKra> kras = kraRepository
                .findByEmployeeIdAndPeriodYearAndPeriodIndexAndPeriodTypeAndActiveTrueOrderByDisplayOrderAsc(
                        target, (short) periodYear, (byte) periodIndex, type);

        List<KraProgressResponse> progress = new ArrayList<>();
        List<BigDecimal> scores = new ArrayList<>();
        List<BigDecimal> weights = new ArrayList<>();
        BigDecimal weightageTotal = BigDecimal.ZERO;
        int kpiCount = 0;
        int scoredKpiCount = 0;

        for (EmployeeKra kra : kras) {
            KraProgressResponse row = toProgress(kra, employee);
            progress.add(row);
            scores.add(row.achievementPercent());
            weights.add(kra.getWeightage());
            weightageTotal = weightageTotal.add(kra.getWeightage());
            kpiCount += row.totalKpiCount();
            scoredKpiCount += row.scoredKpiCount();
        }

        BigDecimal overall = calculator.overall(scores, weights);

        return new KraScorecardResponse(employee.getId(), employee.fullName(),
                employee.getEmployeeCode(),
                employee.getDesignation() == null ? null : employee.getDesignation().getName(),
                type, periodYear, periodIndex, type.label(periodYear, periodIndex),
                type.startOf(periodYear, periodIndex), type.endOf(periodYear, periodIndex),
                overall, overall != null, weightageTotal,
                kras.isEmpty() || weightageTotal.subtract(HUNDRED).abs().compareTo(TOLERANCE) <= 0,
                kras.size(), kpiCount, scoredKpiCount, progress);
    }

    /** Active KRAs on a date, offered by the work log for attribution. */
    @Transactional(readOnly = true)
    public List<OptionDto> activeOptions(LocalDate onDate) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long employeeId = requireEmployeeId(principal);
        LocalDate date = onDate == null ? LocalDate.now() : onDate;

        return kraRepository.findActiveOn(employeeId, date).stream()
                .map(kra -> new OptionDto(kra.getId(), kra.periodLabel(), kra.getName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<KpiHistoryResponse> kpiHistory(Long kpiId) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        EmployeeKpi kpi = requireKpi(kpiId, principal.getCompanyId());
        if (!kpi.getEmployeeId().equals(principal.getEmployeeId())) {
            accessScopeResolver.assertCanAccess(kpi.getEmployeeId());
        }

        Map<String, KpiActual> actuals = new HashMap<>();
        actualRepository.findByEmployeeKpiIdOrderByPeriodStartAsc(kpiId)
                .forEach(actual -> actuals.put(key(actual.getPeriodStart(), actual.getPeriodEnd()), actual));

        return targetRepository.findByEmployeeKpiIdOrderByPeriodStartAsc(kpiId).stream()
                .map(target -> {
                    KpiActual actual = actuals.get(key(target.getPeriodStart(), target.getPeriodEnd()));
                    BigDecimal actualValue = actual == null ? null : actual.getActualValue();
                    var outcome = calculator.achievement(target.getTargetValue(), actualValue,
                            kpi.getTargetDirection(), kpi.getCapPercent(), kpi.getWeightage());
                    return new KpiHistoryResponse(target.getPeriodStart(), target.getPeriodEnd(),
                            target.getTargetValue(), actualValue, outcome.achievementPercent(),
                            actual == null ? null : actual.getSource(),
                            actual == null ? null : actual.getRecordedByName(),
                            actual == null ? null : actual.getNote());
                })
                .toList();
    }

    // ----------------------------------------------------------- assignment

    @Transactional
    public KraScorecardResponse assign(Long employeeId, AssignKraRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        Long companyId = principal.getCompanyId();
        accessScopeResolver.assertCanAccess(employeeId);

        Employee employee = employeeRepository.findByIdAndCompanyId(employeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee", employeeId));

        PeriodType type = request.periodType();
        short year = request.periodYear().shortValue();
        byte index = request.periodIndex().byteValue();
        validatePeriodIndex(type, index);

        boolean exists = kraRepository.existsByEmployeeIdAndPeriodYearAndPeriodIndexAndPeriodTypeAndActiveTrue(
                employeeId, year, index, type);
        if (exists) {
            if (!request.replaceExisting()) {
                throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE,
                        employee.fullName() + " already has KRAs for " + type.label(year, index)
                                + ". Choose replace if you mean to overwrite them.");
            }
            // Deactivate rather than delete: targets and actuals already
            // recorded against the old assignment stay auditable.
            List<EmployeeKra> existing = kraRepository
                    .findByEmployeeIdAndPeriodYearAndPeriodIndexAndPeriodTypeAndActiveTrueOrderByDisplayOrderAsc(
                            employeeId, year, index, type);
            existing.forEach(kra -> {
                kra.setActive(false);
                kra.setStatus(KraStatus.CLOSED);
            });
            kraRepository.saveAll(existing);
            log.info("Replaced {} existing KRAs for employee {} in {}", existing.size(), employeeId,
                    type.label(year, index));
        }

        KraTemplate template = templateService.requirePublished(request.templateId(), companyId);
        LocalDate start = type.startOf(year, index);
        LocalDate end = type.endOf(year, index);

        List<EmployeeKra> created = new ArrayList<>();
        for (KraTemplateItem item : template.getItems()) {
            EmployeeKra kra = new EmployeeKra();
            kra.setCompanyId(companyId);
            kra.setEmployee(employee);
            kra.setKraTemplateId(template.getId());
            kra.setName(item.getName());
            kra.setDescription(item.getDescription());
            kra.setWeightage(item.getWeightage());
            kra.setPeriodType(type);
            kra.setPeriodYear(year);
            kra.setPeriodIndex(index);
            kra.setPeriodStart(start);
            kra.setPeriodEnd(end);
            kra.setStatus(KraStatus.ACTIVE);
            kra.setDisplayOrder(item.getDisplayOrder());
            kra.setAssignedBy(principal.getEmployeeId());

            for (KpiTemplateItem kpiItem : item.getKpis()) {
                EmployeeKpi kpi = new EmployeeKpi();
                kpi.setCompanyId(companyId);
                kpi.setEmployeeKra(kra);
                kpi.setEmployeeId(employeeId);
                kpi.setName(kpiItem.getName());
                kpi.setDescription(kpiItem.getDescription());
                kpi.setMeasurementType(kpiItem.getMeasurementType());
                kpi.setTargetDirection(kpiItem.getTargetDirection());
                kpi.setUnit(kpiItem.getUnit());
                kpi.setWeightage(kpiItem.getWeightage());
                kpi.setCapPercent(kpiItem.getCapPercent());
                kpi.setActualSource(kpiItem.getActualSource());
                kpi.setDisplayOrder(kpiItem.getDisplayOrder());
                kra.getKpis().add(kpi);
            }

            created.add(kraRepository.save(kra));
        }

        if (request.seedTargets()) {
            seedTargets(template, created, start, end, companyId);
        }

        auditService.record(AuditModule.KRA, AuditAction.ASSIGN, "EmployeeKra", employeeId, null,
                Map.of("template", template.getCode(), "period", type.label(year, index),
                        "kraCount", String.valueOf(created.size())), null);
        log.info("Assigned template {} to employee {} for {}", template.getCode(), employeeId,
                type.label(year, index));

        return scorecard(employeeId, type, (int) year, (int) index);
    }

    /**
     * Adjust the weightages of one employee's KRAs. Sent as a complete set,
     * because the only valid state is one that totals 100.
     */
    @Transactional
    public KraScorecardResponse updateWeightages(Long employeeId, WeightageUpdateRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        accessScopeResolver.assertCanAccess(employeeId);

        List<EmployeeKra> kras = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (WeightageUpdateRequest.Item item : request.items()) {
            EmployeeKra kra = kraRepository.findByIdAndCompanyId(item.id(), principal.getCompanyId())
                    .orElseThrow(() -> new ResourceNotFoundException("KRA", item.id()));
            if (!kra.getEmployee().getId().equals(employeeId)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "That KRA belongs to a different employee");
            }
            kras.add(kra);
            total = total.add(item.weightage());
        }

        if (total.subtract(HUNDRED).abs().compareTo(TOLERANCE) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "KRA weightages must total 100%. They currently total "
                            + total.stripTrailingZeros().toPlainString() + "%.");
        }

        Map<String, String> before = new HashMap<>();
        Map<String, String> after = new HashMap<>();
        for (int i = 0; i < kras.size(); i++) {
            EmployeeKra kra = kras.get(i);
            before.put(kra.getName(), kra.getWeightage().toPlainString());
            kra.setWeightage(request.items().get(i).weightage());
            after.put(kra.getName(), kra.getWeightage().toPlainString());
        }
        kraRepository.saveAll(kras);

        // Weightage changes move somebody's score, so the reason is kept.
        auditService.record(AuditModule.KRA, AuditAction.UPDATE, "EmployeeKra", employeeId,
                before, after, request.reason());

        EmployeeKra first = kras.get(0);
        return scorecard(employeeId, first.getPeriodType(), (int) first.getPeriodYear(),
                (int) first.getPeriodIndex());
    }

    // ------------------------------------------------------ targets/actuals

    @Transactional
    public KpiProgressResponse setTarget(Long kpiId, KpiTargetRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        EmployeeKpi kpi = requireKpi(kpiId, principal.getCompanyId());
        accessScopeResolver.assertCanAccess(kpi.getEmployeeId());

        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "The period end cannot be before the period start");
        }

        KpiTarget target = targetRepository
                .findByEmployeeKpiIdAndPeriodStartAndPeriodEnd(kpiId, request.periodStart(),
                        request.periodEnd())
                .orElseGet(() -> {
                    KpiTarget fresh = new KpiTarget();
                    fresh.setCompanyId(principal.getCompanyId());
                    fresh.setEmployeeKpi(kpi);
                    fresh.setPeriodStart(request.periodStart());
                    fresh.setPeriodEnd(request.periodEnd());
                    return fresh;
                });

        String previous = target.getId() == null ? null : target.getTargetValue().toPlainString();
        target.setPeriodType(request.periodType());
        target.setTargetValue(request.targetValue());
        target.setStretchValue(request.stretchValue());
        target.setNotes(request.notes());
        targetRepository.save(target);

        auditService.record(AuditModule.KPI, previous == null ? AuditAction.CREATE : AuditAction.UPDATE,
                "KpiTarget", kpi.getId(),
                previous == null ? null : Map.of("target", previous),
                Map.of("target", request.targetValue().toPlainString()), request.notes());

        return progressFor(kpi, kpi.getEmployeeKra());
    }

    @Transactional
    public KpiProgressResponse recordActual(Long kpiId, KpiActualRequest request) {
        AppUserPrincipal principal = SecurityUtils.requirePrincipal();
        EmployeeKpi kpi = requireKpi(kpiId, principal.getCompanyId());
        accessScopeResolver.assertCanAccess(kpi.getEmployeeId());

        if (kpi.getActualSource().isDerived()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This KPI is measured automatically from recorded work and cannot be entered by hand");
        }
        if (kpi.getEmployeeId().equals(principal.getEmployeeId())) {
            // An employee entering their own achievement would make the score
            // an assertion rather than a measurement.
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Actuals are recorded by your manager, not by you");
        }

        KpiActual actual = actualRepository
                .findByEmployeeKpiIdAndPeriodStartAndPeriodEnd(kpiId, request.periodStart(),
                        request.periodEnd())
                .orElseGet(() -> {
                    KpiActual fresh = new KpiActual();
                    fresh.setCompanyId(principal.getCompanyId());
                    fresh.setEmployeeKpi(kpi);
                    fresh.setPeriodStart(request.periodStart());
                    fresh.setPeriodEnd(request.periodEnd());
                    return fresh;
                });

        String previous = actual.getId() == null ? null : actual.getActualValue().toPlainString();
        actual.setActualValue(request.actualValue());
        actual.setSource(ActualSource.MANUAL);
        actual.setRecordedBy(principal.getEmployeeId());
        actual.setRecordedByName(principal.getDisplayName());
        actual.setNote(request.note());
        actualRepository.save(actual);

        auditService.record(AuditModule.KPI, previous == null ? AuditAction.CREATE : AuditAction.UPDATE,
                "KpiActual", kpi.getId(),
                previous == null ? null : Map.of("actual", previous),
                Map.of("actual", request.actualValue().toPlainString()), request.note());

        return progressFor(kpi, kpi.getEmployeeKra());
    }

    // ------------------------------------------------------------ internals

    private void seedTargets(KraTemplate template, List<EmployeeKra> created, LocalDate start,
                             LocalDate end, Long companyId) {
        Map<String, BigDecimal> defaults = new HashMap<>();
        template.getItems().forEach(item -> item.getKpis().forEach(kpi -> {
            if (kpi.getDefaultTargetValue() != null) {
                defaults.put(item.getName() + "::" + kpi.getName(), kpi.getDefaultTargetValue());
            }
        }));

        for (EmployeeKra kra : created) {
            for (EmployeeKpi kpi : kra.getKpis()) {
                BigDecimal value = defaults.get(kra.getName() + "::" + kpi.getName());
                if (value == null) {
                    continue;
                }
                KpiTarget target = new KpiTarget();
                target.setCompanyId(companyId);
                target.setEmployeeKpi(kpi);
                target.setPeriodType(kra.getPeriodType());
                target.setPeriodStart(start);
                target.setPeriodEnd(end);
                target.setTargetValue(value);
                target.setNotes("Seeded from template " + template.getCode());
                targetRepository.save(target);
            }
        }
    }

    private KraProgressResponse toProgress(EmployeeKra kra, Employee employee) {
        List<KpiProgressResponse> kpis = new ArrayList<>();
        List<AchievementCalculator.KpiOutcome> outcomes = new ArrayList<>();
        int scored = 0;

        for (EmployeeKpi kpi : kra.getKpis()) {
            KpiProgressResponse row = progressFor(kpi, kra);
            kpis.add(row);
            outcomes.add(row.scored()
                    ? AchievementCalculator.KpiOutcome.scored(row.achievementPercent(), kpi.getWeightage())
                    : AchievementCalculator.KpiOutcome.excluded(kpi.getWeightage(), row.excludedReason()));
            if (row.scored()) {
                scored++;
            }
        }

        BigDecimal achievement = calculator.rollUp(outcomes);

        return new KraProgressResponse(kra.getId(), employee.getId(), employee.fullName(),
                kra.getName(), kra.getDescription(), kra.getWeightage(), kra.getPeriodType(),
                kra.getPeriodYear(), kra.getPeriodIndex(), kra.periodLabel(), kra.getPeriodStart(),
                kra.getPeriodEnd(), kra.getStatus(), achievement, achievement != null,
                scored, kra.getKpis().size(), kpis);
    }

    private KpiProgressResponse progressFor(EmployeeKpi kpi, EmployeeKra kra) {
        BigDecimal target = progressReader.targetFor(kpi, kra);
        BigDecimal actual = progressReader.actualFor(kpi, kra);

        String recordedBy;
        String note;
        if (kpi.getActualSource().isDerived()) {
            recordedBy = "Measured automatically";
            note = null;
        } else {
            Optional<KpiActual> stored = progressReader.storedActual(kpi, kra);
            recordedBy = stored.map(KpiActual::getRecordedByName).orElse(null);
            note = stored.map(KpiActual::getNote).orElse(null);
        }

        var outcome = calculator.achievement(target, actual, kpi.getTargetDirection(),
                kpi.getCapPercent(), kpi.getWeightage());

        return new KpiProgressResponse(kpi.getId(), kpi.getName(), kpi.getDescription(),
                kpi.getMeasurementType(), kpi.getTargetDirection(), kpi.getUnit(), kpi.getWeightage(),
                kpi.getCapPercent(), kpi.getActualSource(), kpi.getActualSource().isDerived(),
                target, actual, outcome.achievementPercent(), outcome.scored(),
                outcome.excludedReason(), recordedBy, note);
    }

    private void validatePeriodIndex(PeriodType type, byte index) {
        if (index < 1 || index > type.periodsPerYear()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Period " + index + " is not valid for a " + type.name().toLowerCase() + " cycle");
        }
    }

    private int currentIndex(PeriodType type) {
        LocalDate today = LocalDate.now();
        return switch (type) {
            case MONTHLY -> today.getMonthValue();
            case QUARTERLY -> (today.getMonthValue() - 1) / 3 + 1;
            case ANNUAL -> 1;
            case WEEKLY -> today.getDayOfYear() / 7 + 1;
            case DAILY -> today.getDayOfYear();
        };
    }

    private EmployeeKpi requireKpi(Long kpiId, Long companyId) {
        return kpiRepository.findByIdAndCompanyId(kpiId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("KPI", kpiId));
    }

    private Long requireEmployeeId(AppUserPrincipal principal) {
        if (principal.getEmployeeId() == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND,
                    "This account is not linked to an employee record");
        }
        return principal.getEmployeeId();
    }

    private String key(LocalDate start, LocalDate end) {
        return start + "::" + end;
    }
}
