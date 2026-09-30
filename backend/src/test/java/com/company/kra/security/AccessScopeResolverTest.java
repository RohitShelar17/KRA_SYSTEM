package com.company.kra.security;

import com.company.kra.common.enums.DataScope;
import com.company.kra.exception.AccessScopeException;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Scope resolution is the control that stops one manager reading another
 * team's records, so it is tested per scope rather than only end to end.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccessScopeResolverTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private AccessScopeResolver resolver;

    @BeforeEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Organisation scope needs no id set")
    void organisationScopeIsUnbounded() {
        AppUserPrincipal principal = principal(DataScope.ORGANISATION, 10L, 3L);

        AccessScopeResolver.Scope scope = resolver.visibleEmployeeIds(principal);

        assertThat(scope.organisationWide()).isTrue();
        assertThat(scope.covers(999L)).isTrue();
    }

    @Test
    @DisplayName("Self scope sees only the caller")
    void selfScopeSeesOnlyOwnRecord() {
        AppUserPrincipal principal = principal(DataScope.SELF, 10L, 3L);

        AccessScopeResolver.Scope scope = resolver.visibleEmployeeIds(principal);

        assertThat(scope.organisationWide()).isFalse();
        assertThat(scope.employeeIds()).containsExactly(10L);
        assertThat(scope.covers(11L)).isFalse();
    }

    @Test
    @DisplayName("Reports scope walks the subtree, direct and indirect")
    void reportsScopeIncludesIndirectReports() {
        when(employeeRepository.findIdsByManagerIds(Set.of(10L))).thenReturn(mutable(20L, 21L));
        when(employeeRepository.findIdsByManagerIds(Set.of(20L, 21L))).thenReturn(mutable(30L));
        when(employeeRepository.findIdsByManagerIds(Set.of(30L))).thenReturn(new HashSet<>());

        AccessScopeResolver.Scope scope = resolver.visibleEmployeeIds(principal(DataScope.REPORTS, 10L, 3L));

        assertThat(scope.employeeIds()).containsExactlyInAnyOrder(10L, 20L, 21L, 30L);
        assertThat(scope.covers(30L)).isTrue();
        assertThat(scope.covers(40L)).isFalse();
    }

    @Test
    @DisplayName("A reporting cycle stops at the depth guard instead of looping")
    void reportingCycleTerminates() {
        when(employeeRepository.findIdsByManagerIds(any())).thenAnswer(invocation -> mutable(20L, 10L));

        AccessScopeResolver.Scope scope = resolver.visibleEmployeeIds(principal(DataScope.REPORTS, 10L, 3L));

        assertThat(scope.employeeIds()).contains(10L, 20L);
    }

    @Test
    @DisplayName("Department scope covers the whole department")
    void departmentScopeCoversDepartment() {
        when(employeeRepository.findIdsByDepartmentId(3L)).thenReturn(mutable(10L, 11L, 12L));

        AccessScopeResolver.Scope scope = resolver.visibleEmployeeIds(principal(DataScope.DEPARTMENT, 10L, 3L));

        assertThat(scope.employeeIds()).containsExactlyInAnyOrder(10L, 11L, 12L);
    }

    @Test
    @DisplayName("Out-of-scope access is refused rather than filtered")
    void assertCanAccessThrowsForForeignRecord() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal(DataScope.SELF, 10L, 3L), null, Set.of()));

        assertThatThrownBy(() -> resolver.assertCanAccess(99L))
                .isInstanceOf(AccessScopeException.class)
                .hasMessageContaining("outside your access scope");
    }

    private AppUserPrincipal principal(DataScope scope, Long employeeId, Long departmentId) {
        return AppUserPrincipal.builder()
                .userId(1L)
                .employeeId(employeeId)
                .companyId(1L)
                .departmentId(departmentId)
                .username("tester")
                .email("tester@company.com")
                .displayName("Test User")
                .passwordHash("hash")
                .enabled(true)
                .accountLocked(false)
                .mustChangePassword(false)
                .dataScope(scope)
                .roles(Set.of("EMPLOYEE"))
                .permissions(Set.of("EMPLOYEE_READ"))
                .build();
    }

    private Set<Long> mutable(Long... ids) {
        return new HashSet<>(Set.of(ids));
    }
}
