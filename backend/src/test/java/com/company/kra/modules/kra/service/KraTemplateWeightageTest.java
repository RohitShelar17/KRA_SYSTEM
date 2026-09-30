package com.company.kra.modules.kra.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.ActualSource;
import com.company.kra.common.enums.MeasurementType;
import com.company.kra.common.enums.PeriodType;
import com.company.kra.common.enums.TargetDirection;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.modules.kra.dto.KpiTemplateItemRequest;
import com.company.kra.modules.kra.dto.KraTemplateItemRequest;
import com.company.kra.modules.kra.dto.KraTemplateRequest;
import com.company.kra.modules.kra.entity.KraTemplate;
import com.company.kra.modules.kra.repository.KraTemplateRepository;
import com.company.kra.modules.organisation.repository.DepartmentRepository;
import com.company.kra.modules.organisation.repository.DesignationRepository;
import com.company.kra.security.AppUserPrincipal;
import com.company.kra.common.enums.DataScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The 100% rule. A scorecard whose weightages do not add up cannot produce a
 * defensible score, so an unbalanced template is refused rather than saved.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KraTemplateWeightageTest {

    @Mock private KraTemplateRepository templateRepository;
    @Mock private DesignationRepository designationRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private AuditService auditService;

    private KraTemplateService service;

    @BeforeEach
    void setUp() {
        service = new KraTemplateService(templateRepository, designationRepository,
                departmentRepository, auditService);

        when(templateRepository.existsByCodeIgnoreCaseAndCompanyId(any(), any())).thenReturn(false);
        when(templateRepository.save(any())).thenAnswer(call -> {
            KraTemplate saved = call.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal(), null, Set.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("A balanced template saves")
    void balancedTemplateSaves() {
        assertThatCode(() -> service.create(template(
                kra("Delivery", "60", kpi("Throughput", "100")),
                kra("Quality", "40", kpi("Defect rate", "100")))))
                .doesNotThrowAnyException();

        verify(templateRepository).save(any());
    }

    @Test
    @DisplayName("KRA weightages under 100 are refused, and the total is named")
    void kraWeightagesUnderHundred() {
        assertThatThrownBy(() -> service.create(template(
                kra("Delivery", "50", kpi("Throughput", "100")),
                kra("Quality", "40", kpi("Defect rate", "100")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must total 100%")
                .hasMessageContaining("90");

        verify(templateRepository, never()).save(any());
    }

    @Test
    @DisplayName("KRA weightages over 100 are refused")
    void kraWeightagesOverHundred() {
        assertThatThrownBy(() -> service.create(template(
                kra("Delivery", "70", kpi("Throughput", "100")),
                kra("Quality", "40", kpi("Defect rate", "100")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("110");
    }

    @Test
    @DisplayName("KPI weightages inside a KRA must also total 100, and the KRA is named")
    void kpiWeightagesMustBalance() {
        assertThatThrownBy(() -> service.create(template(
                kra("Delivery", "100",
                        kpi("Throughput", "60"),
                        kpi("Cycle time", "30")))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Delivery")
                .hasMessageContaining("90");
    }

    @Test
    @DisplayName("Fractional weightages that sum to 100 are accepted")
    void fractionalWeightagesAccepted() {
        assertThatCode(() -> service.create(template(
                kra("A", "33.33", kpi("x", "100")),
                kra("B", "33.33", kpi("y", "100")),
                kra("C", "33.34", kpi("z", "100")))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("An unbalanced template cannot be published")
    void publishRefusesUnbalancedTemplate() {
        KraTemplate unbalanced = new KraTemplate();
        unbalanced.setId(2L);
        unbalanced.setCompanyId(1L);
        unbalanced.setCode("BAD");
        unbalanced.setName("Unbalanced");
        when(templateRepository.findByIdAndCompanyId(2L, 1L)).thenReturn(java.util.Optional.of(unbalanced));

        assertThatThrownBy(() -> service.setPublished(2L, true))
                .isInstanceOf(BusinessException.class)
                .extracting(caught -> ((BusinessException) caught).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);
    }

    @Test
    @DisplayName("An unpublished template cannot be assigned")
    void unpublishedTemplateCannotBeAssigned() {
        KraTemplate draft = new KraTemplate();
        draft.setId(3L);
        draft.setCompanyId(1L);
        draft.setCode("DRAFT");
        draft.setPublished(false);
        when(templateRepository.findByIdAndCompanyId(3L, 1L)).thenReturn(java.util.Optional.of(draft));

        assertThatThrownBy(() -> service.requirePublished(3L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Publish this template");
    }

    @Test
    @DisplayName("The saved template keeps its KRA and KPI structure")
    void structureIsCopiedThrough() {
        var response = service.create(template(
                kra("Delivery", "60", kpi("Throughput", "70"), kpi("Cycle time", "30")),
                kra("Quality", "40", kpi("Defect rate", "100"))));

        assertThat(response.kraCount()).isEqualTo(2);
        assertThat(response.kpiCount()).isEqualTo(3);
        assertThat(response.weightageTotal()).isEqualByComparingTo("100");
        assertThat(response.items().get(0).kpis()).hasSize(2);
    }

    // ------------------------------------------------------------- fixtures

    private KraTemplateRequest template(KraTemplateItemRequest... items) {
        return new KraTemplateRequest("TPL", "Test scorecard", null, null, null,
                PeriodType.QUARTERLY, false, List.of(items));
    }

    private KraTemplateItemRequest kra(String name, String weightage, KpiTemplateItemRequest... kpis) {
        return new KraTemplateItemRequest(null, name, null, new BigDecimal(weightage), 0, List.of(kpis));
    }

    private KpiTemplateItemRequest kpi(String name, String weightage) {
        return new KpiTemplateItemRequest(null, name, null, MeasurementType.PERCENTAGE,
                TargetDirection.HIGHER_IS_BETTER, "%", new BigDecimal("90"),
                new BigDecimal(weightage), new BigDecimal("120"), ActualSource.MANUAL, 0);
    }

    private AppUserPrincipal principal() {
        return AppUserPrincipal.builder()
                .userId(1L)
                .employeeId(1L)
                .companyId(1L)
                .departmentId(1L)
                .username("hr")
                .email("hr@company.com")
                .displayName("HR Admin")
                .passwordHash("hash")
                .enabled(true)
                .accountLocked(false)
                .mustChangePassword(false)
                .dataScope(DataScope.ORGANISATION)
                .roles(Set.of("HR_ADMIN"))
                .permissions(Set.of("KRA_TEMPLATE_MANAGE"))
                .build();
    }
}
