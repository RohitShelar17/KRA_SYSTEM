package com.company.kra.modules.kra.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.dto.OptionDto;
import com.company.kra.common.enums.ActualSource;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.DuplicateResourceException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.kra.dto.KpiTemplateItemRequest;
import com.company.kra.modules.kra.dto.KpiTemplateItemResponse;
import com.company.kra.modules.kra.dto.KraTemplateItemRequest;
import com.company.kra.modules.kra.dto.KraTemplateItemResponse;
import com.company.kra.modules.kra.dto.KraTemplateRequest;
import com.company.kra.modules.kra.dto.KraTemplateResponse;
import com.company.kra.modules.kra.entity.KpiTemplateItem;
import com.company.kra.modules.kra.entity.KraTemplate;
import com.company.kra.modules.kra.entity.KraTemplateItem;
import com.company.kra.modules.kra.repository.KraTemplateRepository;
import com.company.kra.modules.organisation.entity.Department;
import com.company.kra.modules.organisation.entity.Designation;
import com.company.kra.modules.organisation.repository.DepartmentRepository;
import com.company.kra.modules.organisation.repository.DesignationRepository;
import com.company.kra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * KRA and KPI templates: what a role is measured on.
 *
 * <p>The rule that matters here is arithmetic. KRA weightages inside a
 * template must total exactly 100, and KPI weightages inside each KRA must
 * total exactly 100. Both are checked on every save, inside the transaction —
 * a partially-weighted scorecard is not a state worth persisting.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KraTemplateService {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final KraTemplateRepository templateRepository;
    private final DesignationRepository designationRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<KraTemplateResponse> list() {
        Long companyId = SecurityUtils.requireCompanyId();
        return templateRepository.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId).stream()
                .map(template -> toResponse(template, companyId, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public KraTemplateResponse get(Long id) {
        Long companyId = SecurityUtils.requireCompanyId();
        return toResponse(require(id, companyId), companyId, true);
    }

    /** Published templates that suit a designation, most specific first. */
    @Transactional(readOnly = true)
    public List<OptionDto> assignableFor(Long designationId) {
        Long companyId = SecurityUtils.requireCompanyId();
        return templateRepository.findAssignableFor(companyId, designationId).stream()
                .map(template -> new OptionDto(template.getId(), template.getCode(),
                        template.getName() + " · " + template.getPeriodType().name().toLowerCase()))
                .toList();
    }

    @Transactional
    public KraTemplateResponse create(KraTemplateRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        if (templateRepository.existsByCodeIgnoreCaseAndCompanyId(request.code(), companyId)) {
            throw new DuplicateResourceException("KRA template", "code", request.code());
        }
        validateReferences(request, companyId);
        validateWeightages(request);

        KraTemplate template = new KraTemplate();
        template.setCompanyId(companyId);
        template.setCode(request.code().toUpperCase());
        apply(template, request, companyId);

        KraTemplate saved = templateRepository.save(template);
        auditService.record(AuditModule.KRA, AuditAction.CREATE, "KraTemplate", saved.getId(),
                null, summary(saved), null);
        log.info("KRA template {} created with {} KRAs", saved.getCode(), saved.getItems().size());
        return toResponse(saved, companyId, true);
    }

    @Transactional
    public KraTemplateResponse update(Long id, KraTemplateRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        KraTemplate template = require(id, companyId);

        if (!template.getCode().equalsIgnoreCase(request.code())
                && templateRepository.existsByCodeIgnoreCaseAndCompanyId(request.code(), companyId)) {
            throw new DuplicateResourceException("KRA template", "code", request.code());
        }
        validateReferences(request, companyId);
        validateWeightages(request);

        var before = summary(template);
        template.setCode(request.code().toUpperCase());
        apply(template, request, companyId);
        templateRepository.save(template);

        auditService.record(AuditModule.KRA, AuditAction.UPDATE, "KraTemplate", id,
                before, summary(template), null);
        return toResponse(template, companyId, true);
    }

    @Transactional
    public KraTemplateResponse setPublished(Long id, boolean published) {
        Long companyId = SecurityUtils.requireCompanyId();
        KraTemplate template = require(id, companyId);

        if (published) {
            // Publishing is the gate: an unbalanced template must never reach
            // an employee's scorecard.
            assertTotals(template);
        }
        template.setPublished(published);
        templateRepository.save(template);

        auditService.record(AuditModule.KRA, AuditAction.UPDATE, "KraTemplate", id,
                java.util.Map.of("published", String.valueOf(!published)),
                java.util.Map.of("published", String.valueOf(published)), null);
        return toResponse(template, companyId, true);
    }

    @Transactional
    public void deactivate(Long id) {
        Long companyId = SecurityUtils.requireCompanyId();
        KraTemplate template = require(id, companyId);
        template.setActive(false);
        template.setPublished(false);
        templateRepository.save(template);
        auditService.record(AuditModule.KRA, AuditAction.DELETE, "KraTemplate", id, null, null,
                "Deactivated");
    }

    /** Used by the assignment service, which must not touch this repository. */
    @Transactional(readOnly = true)
    public KraTemplate requirePublished(Long id, Long companyId) {
        KraTemplate template = require(id, companyId);
        if (!template.isPublished()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Publish this template before assigning it");
        }
        assertTotals(template);
        return template;
    }

    // ------------------------------------------------------------ internals

    private void apply(KraTemplate template, KraTemplateRequest request, Long companyId) {
        template.setName(request.name().trim());
        template.setDescription(request.description());
        template.setDesignationId(request.designationId());
        template.setDepartmentId(request.departmentId());
        template.setPeriodType(request.periodType());
        template.setPublished(request.published());

        // Rebuilt wholesale: the request carries the complete intended shape,
        // and orphanRemoval clears anything the editor dropped.
        template.getItems().clear();
        int kraOrder = 0;
        for (KraTemplateItemRequest itemRequest : request.items()) {
            KraTemplateItem item = new KraTemplateItem();
            item.setCompanyId(companyId);
            item.setTemplate(template);
            item.setName(itemRequest.name().trim());
            item.setDescription(itemRequest.description());
            item.setWeightage(itemRequest.weightage());
            item.setDisplayOrder(itemRequest.displayOrder() == 0 ? kraOrder : itemRequest.displayOrder());

            int kpiOrder = 0;
            for (KpiTemplateItemRequest kpiRequest : itemRequest.kpis()) {
                KpiTemplateItem kpi = new KpiTemplateItem();
                kpi.setCompanyId(companyId);
                kpi.setKraItem(item);
                kpi.setName(kpiRequest.name().trim());
                kpi.setDescription(kpiRequest.description());
                kpi.setMeasurementType(kpiRequest.measurementType());
                kpi.setTargetDirection(kpiRequest.targetDirection());
                kpi.setUnit(kpiRequest.unit());
                kpi.setDefaultTargetValue(kpiRequest.defaultTargetValue());
                kpi.setWeightage(kpiRequest.weightage());
                kpi.setCapPercent(kpiRequest.capPercent() == null
                        ? new BigDecimal("120.00") : kpiRequest.capPercent());
                kpi.setActualSource(kpiRequest.actualSource() == null
                        ? ActualSource.MANUAL : kpiRequest.actualSource());
                kpi.setDisplayOrder(kpiRequest.displayOrder() == 0 ? kpiOrder : kpiRequest.displayOrder());
                item.getKpis().add(kpi);
                kpiOrder += 10;
            }

            template.getItems().add(item);
            kraOrder += 10;
        }
    }

    /**
     * The 100% rule, stated once. Both levels are checked before anything is
     * written, and the message names the offending KRA so it can be fixed
     * without hunting.
     */
    private void validateWeightages(KraTemplateRequest request) {
        BigDecimal kraTotal = request.items().stream()
                .map(KraTemplateItemRequest::weightage)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (kraTotal.subtract(HUNDRED).abs().compareTo(TOLERANCE) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "KRA weightages must total 100%. They currently total " + strip(kraTotal) + "%.");
        }

        for (KraTemplateItemRequest item : request.items()) {
            BigDecimal kpiTotal = item.kpis().stream()
                    .map(KpiTemplateItemRequest::weightage)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (kpiTotal.subtract(HUNDRED).abs().compareTo(TOLERANCE) > 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "KPI weightages inside \"" + item.name() + "\" must total 100%. "
                                + "They currently total " + strip(kpiTotal) + "%.");
            }
        }
    }

    private void assertTotals(KraTemplate template) {
        if (template.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This template has no KRAs yet");
        }
        if (template.totalWeightage().subtract(HUNDRED).abs().compareTo(TOLERANCE) > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "KRA weightages total " + strip(template.totalWeightage()) + "%, not 100%");
        }
        for (KraTemplateItem item : template.getItems()) {
            if (item.totalKpiWeightage().subtract(HUNDRED).abs().compareTo(TOLERANCE) > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "KPI weightages inside \"" + item.getName() + "\" total "
                                + strip(item.totalKpiWeightage()) + "%, not 100%");
            }
        }
    }

    private void validateReferences(KraTemplateRequest request, Long companyId) {
        if (request.designationId() != null
                && designationRepository.findByIdAndCompanyId(request.designationId(), companyId).isEmpty()) {
            throw new ResourceNotFoundException("Designation", request.designationId());
        }
        if (request.departmentId() != null
                && departmentRepository.findByIdAndCompanyId(request.departmentId(), companyId).isEmpty()) {
            throw new ResourceNotFoundException("Department", request.departmentId());
        }
    }

    private KraTemplate require(Long id, Long companyId) {
        return templateRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("KRA template", id));
    }

    private java.util.Map<String, String> summary(KraTemplate template) {
        return java.util.Map.of(
                "code", template.getCode(),
                "name", template.getName(),
                "kraCount", String.valueOf(template.getItems().size()),
                "weightageTotal", strip(template.totalWeightage()));
    }

    private KraTemplateResponse toResponse(KraTemplate template, Long companyId, boolean withItems) {
        String designationName = template.getDesignationId() == null ? null
                : designationRepository.findByIdAndCompanyId(template.getDesignationId(), companyId)
                .map(Designation::getName).orElse(null);
        String departmentName = template.getDepartmentId() == null ? null
                : departmentRepository.findByIdAndCompanyId(template.getDepartmentId(), companyId)
                .map(Department::getName).orElse(null);

        List<KraTemplateItemResponse> items = new ArrayList<>();
        int kpiCount = 0;
        for (KraTemplateItem item : template.getItems()) {
            kpiCount += item.getKpis().size();
            if (withItems) {
                items.add(new KraTemplateItemResponse(item.getId(), item.getName(), item.getDescription(),
                        item.getWeightage(), item.getDisplayOrder(), item.totalKpiWeightage(),
                        item.getKpis().stream().map(this::toKpiResponse).toList()));
            }
        }

        return new KraTemplateResponse(template.getId(), template.getCode(), template.getName(),
                template.getDescription(), template.getDesignationId(), designationName,
                template.getDepartmentId(), departmentName, template.getPeriodType(),
                template.isPublished(), template.isActive(), template.totalWeightage(),
                template.getItems().size(), kpiCount, items);
    }

    private KpiTemplateItemResponse toKpiResponse(KpiTemplateItem kpi) {
        return new KpiTemplateItemResponse(kpi.getId(), kpi.getName(), kpi.getDescription(),
                kpi.getMeasurementType(), kpi.getTargetDirection(), kpi.getUnit(),
                kpi.getDefaultTargetValue(), kpi.getWeightage(), kpi.getCapPercent(),
                kpi.getActualSource(), kpi.getDisplayOrder());
    }

    private String strip(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
