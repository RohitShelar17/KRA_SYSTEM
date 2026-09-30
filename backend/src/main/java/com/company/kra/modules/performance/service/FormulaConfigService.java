package com.company.kra.modules.performance.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.AuditAction;
import com.company.kra.common.enums.AuditModule;
import com.company.kra.common.enums.PerformanceParameter;
import com.company.kra.common.enums.PerformanceScope;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.exception.ResourceNotFoundException;
import com.company.kra.modules.performance.dto.FormulaConfigRequest;
import com.company.kra.modules.performance.dto.FormulaConfigResponse;
import com.company.kra.modules.performance.dto.FormulaParameterResponse;
import com.company.kra.modules.performance.entity.PerformanceFormulaConfig;
import com.company.kra.modules.performance.entity.PerformanceFormulaParameter;
import com.company.kra.modules.performance.repository.PerformanceFormulaRepository;
import com.company.kra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The formula as configuration.
 *
 * <p>Two rules keep it honest. Weights must total exactly 100, checked before
 * anything is written. And an edit never mutates the running configuration: it
 * creates a new version and retires the old one, so a snapshot computed last
 * quarter still names the formula it was computed under.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormulaConfigService {

    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private final PerformanceFormulaRepository formulaRepository;
    private final AuditService auditService;

    /** The configuration the engine should use right now for a grain. */
    @Transactional(readOnly = true)
    public PerformanceFormulaConfig activeConfig(Long companyId, PerformanceScope scope) {
        return formulaRepository.findByCompanyIdAndScopeTypeAndActiveTrue(companyId, scope)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "No active " + scope.name().toLowerCase() + " formula is configured"));
    }

    @Transactional(readOnly = true)
    public List<FormulaConfigResponse> list() {
        Long companyId = SecurityUtils.requireCompanyId();
        return formulaRepository.findByCompanyIdOrderByScopeTypeAscVersionDesc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FormulaConfigResponse active(PerformanceScope scope) {
        return toResponse(activeConfig(SecurityUtils.requireCompanyId(), scope));
    }

    /** Parameters the engine knows how to measure at a given grain. */
    @Transactional(readOnly = true)
    public List<FormulaParameterResponse> availableParameters(PerformanceScope scope) {
        return PerformanceParameter.validFor(scope).stream()
                .sorted()
                .map(parameter -> new FormulaParameterResponse(null, parameter, parameter.name(),
                        parameter.description(), BigDecimal.ZERO, 0))
                .toList();
    }

    /**
     * Saves a new version of a formula and activates it. The previous version
     * is deactivated rather than deleted.
     */
    @Transactional
    public FormulaConfigResponse saveNewVersion(FormulaConfigRequest request) {
        Long companyId = SecurityUtils.requireCompanyId();
        validate(request);

        PerformanceFormulaConfig previous = formulaRepository
                .findByCompanyIdAndScopeTypeAndActiveTrue(companyId, request.scopeType())
                .orElse(null);

        int nextVersion = formulaRepository.currentVersion(companyId, request.scopeType()) + 1;

        PerformanceFormulaConfig config = new PerformanceFormulaConfig();
        config.setCompanyId(companyId);
        config.setScopeType(request.scopeType());
        config.setVersion(nextVersion);
        config.setName(request.name().trim());
        config.setNotes(request.notes());
        config.setMinSubmissionRatePct(request.minSubmissionRatePct() == null
                ? BigDecimal.ZERO : request.minSubmissionRatePct());
        config.setSubmissionPenaltyPct(request.submissionPenaltyPct() == null
                ? BigDecimal.ZERO : request.submissionPenaltyPct());
        config.setEffectiveFrom(request.effectiveFrom() == null ? LocalDate.now() : request.effectiveFrom());
        config.setActive(true);

        int order = 0;
        for (FormulaConfigRequest.Parameter item : request.parameters()) {
            PerformanceFormulaParameter parameter = new PerformanceFormulaParameter();
            parameter.setCompanyId(companyId);
            parameter.setConfig(config);
            parameter.setParameterCode(item.parameterCode());
            parameter.setLabel(item.label() == null || item.label().isBlank()
                    ? item.parameterCode().name() : item.label().trim());
            parameter.setWeightage(item.weightage());
            parameter.setDisplayOrder(order);
            config.getParameters().add(parameter);
            order += 10;
        }

        PerformanceFormulaConfig saved = formulaRepository.save(config);

        if (previous != null) {
            previous.setActive(false);
            formulaRepository.save(previous);
        }

        // A formula change moves everyone's future scores, so the whole shape
        // of it goes into the audit trail, not just the fact that it changed.
        auditService.record(AuditModule.SETTINGS, AuditAction.UPDATE, "PerformanceFormulaConfig",
                saved.getId(),
                previous == null ? null : describe(previous), describe(saved), request.notes());

        log.info("Formula {} v{} activated with {} parameters", request.scopeType(), nextVersion,
                saved.getParameters().size());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public FormulaConfigResponse get(Long id) {
        Long companyId = SecurityUtils.requireCompanyId();
        return toResponse(formulaRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Formula configuration", id)));
    }

    // ------------------------------------------------------------ internals

    private void validate(FormulaConfigRequest request) {
        if (request.parameters().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "A formula needs at least one parameter");
        }

        Set<PerformanceParameter> allowed = PerformanceParameter.validFor(request.scopeType());
        Set<PerformanceParameter> seen = new HashSet<>();

        BigDecimal total = BigDecimal.ZERO;
        for (FormulaConfigRequest.Parameter parameter : request.parameters()) {
            if (!allowed.contains(parameter.parameterCode())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        parameter.parameterCode().name() + " cannot be measured at the "
                                + request.scopeType().name().toLowerCase() + " grain");
            }
            if (!seen.add(parameter.parameterCode())) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        parameter.parameterCode().name() + " appears twice");
            }
            if (parameter.weightage().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Every parameter needs a weight above zero; remove it instead");
            }
            total = total.add(parameter.weightage());
        }

        if (total.subtract(HUNDRED).abs().compareTo(TOLERANCE) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Formula weights must total 100%. They currently total "
                            + total.stripTrailingZeros().toPlainString() + "%.");
        }
    }

    private Map<String, String> describe(PerformanceFormulaConfig config) {
        StringBuilder shape = new StringBuilder();
        config.getParameters().forEach(parameter -> shape
                .append(parameter.getParameterCode().name())
                .append('=')
                .append(parameter.getWeightage().stripTrailingZeros().toPlainString())
                .append("% "));
        return Map.of(
                "scope", config.getScopeType().name(),
                "version", String.valueOf(config.getVersion()),
                "parameters", shape.toString().trim());
    }

    private FormulaConfigResponse toResponse(PerformanceFormulaConfig config) {
        List<FormulaParameterResponse> parameters = config.getParameters().stream()
                .map(parameter -> new FormulaParameterResponse(parameter.getId(),
                        parameter.getParameterCode(), parameter.getLabel(),
                        parameter.getParameterCode().description(), parameter.getWeightage(),
                        parameter.getDisplayOrder()))
                .toList();

        return new FormulaConfigResponse(config.getId(), config.getScopeType(), config.getVersion(),
                config.getName(), config.getNotes(), config.getMinSubmissionRatePct(),
                config.getSubmissionPenaltyPct(), config.isActive(), config.getEffectiveFrom(),
                config.totalWeightage(), parameters);
    }
}
