package com.company.kra.modules.appraisal.service;

import com.company.kra.audit.AuditService;
import com.company.kra.common.enums.DataScope;
import com.company.kra.common.enums.WorkflowStepType;
import com.company.kra.exception.BusinessException;
import com.company.kra.modules.appraisal.dto.WorkflowStepRequest;
import com.company.kra.modules.appraisal.entity.ReviewWorkflowStep;
import com.company.kra.modules.appraisal.repository.ReviewWorkflowStepRepository;
import com.company.kra.security.AppUserPrincipal;
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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A configurable workflow can be configured into something that does not work.
 * These are the shapes that are refused.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowConfigValidationTest {

    @Mock private ReviewWorkflowStepRepository stepRepository;
    @Mock private AuditService auditService;

    private WorkflowConfigService service;

    @BeforeEach
    void setUp() {
        service = new WorkflowConfigService(stepRepository, auditService);
        when(stepRepository.findByCompanyIdOrderByStepOrderAsc(anyLong())).thenReturn(List.of());
        when(stepRepository.save(any())).thenAnswer(call -> {
            ReviewWorkflowStep step = call.getArgument(0);
            step.setId(1L);
            return step;
        });

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal(), null, Set.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("The default flow saves, in the order it was sent")
    void defaultFlowSaves() {
        var saved = service.replaceAll(List.of(
                step(WorkflowStepType.SELF_ASSESSMENT, true, true),
                step(WorkflowStepType.MANAGER_REVIEW, true, true),
                step(WorkflowStepType.DEPARTMENT_HEAD_REVIEW, false, true),
                step(WorkflowStepType.HR_REVIEW, true, true)));

        assertThat(saved).hasSize(4);
        assertThat(saved.get(0).stepType()).isEqualTo(WorkflowStepType.SELF_ASSESSMENT);
        assertThat(saved.get(3).stepType()).isEqualTo(WorkflowStepType.HR_REVIEW);
        assertThat(saved.get(0).stepOrder()).isLessThan(saved.get(3).stepOrder());
    }

    @Test
    @DisplayName("A flow with no active step is refused")
    void emptyFlowRefused() {
        assertThatThrownBy(() -> service.replaceAll(List.of(
                step(WorkflowStepType.SELF_ASSESSMENT, true, false))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one active step");

        verify(stepRepository, never()).save(any());
    }

    @Test
    @DisplayName("Self assessment alone is refused: nobody would review it")
    void selfAssessmentOnlyRefused() {
        assertThatThrownBy(() -> service.replaceAll(List.of(
                step(WorkflowStepType.SELF_ASSESSMENT, true, true))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("rate themselves into a final rating");
    }

    @Test
    @DisplayName("The last step signs off, so it cannot be optional")
    void lastStepMustBeRequired() {
        assertThatThrownBy(() -> service.replaceAll(List.of(
                step(WorkflowStepType.SELF_ASSESSMENT, true, true),
                step(WorkflowStepType.MANAGER_REVIEW, false, true))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be optional");
    }

    @Test
    @DisplayName("A duplicated step is refused")
    void duplicateStepRefused() {
        assertThatThrownBy(() -> service.replaceAll(List.of(
                step(WorkflowStepType.MANAGER_REVIEW, true, true),
                step(WorkflowStepType.MANAGER_REVIEW, true, true))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("appears twice");
    }

    @Test
    @DisplayName("A review-only flow is valid: not every organisation asks for a self assessment")
    void reviewOnlyFlowIsValid() {
        assertThatCode(() -> service.replaceAll(List.of(
                step(WorkflowStepType.MANAGER_REVIEW, true, true),
                step(WorkflowStepType.HR_REVIEW, true, true))))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------- fixtures

    private WorkflowStepRequest step(WorkflowStepType type, boolean required, boolean active) {
        return new WorkflowStepRequest(type, humanise(type), null, required, active, 7);
    }

    private String humanise(WorkflowStepType type) {
        return switch (type) {
            case SELF_ASSESSMENT -> "Self assessment";
            case MANAGER_REVIEW -> "Manager review";
            case DEPARTMENT_HEAD_REVIEW -> "Department head review";
            case HR_REVIEW -> "HR review";
        };
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
                .permissions(Set.of("SETTINGS_MANAGE"))
                .build();
    }
}
