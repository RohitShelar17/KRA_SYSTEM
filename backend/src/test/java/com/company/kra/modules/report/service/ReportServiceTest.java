package com.company.kra.modules.report.service;

import com.company.kra.common.enums.DataScope;
import com.company.kra.common.enums.ReportFilterKey;
import com.company.kra.common.report.ReportColumn;
import com.company.kra.common.report.ReportRow;
import com.company.kra.common.report.ReportTable;
import com.company.kra.exception.BusinessException;
import com.company.kra.modules.report.definition.ReportContext;
import com.company.kra.modules.report.definition.ReportDefinition;
import com.company.kra.modules.report.definition.SimpleReport;
import com.company.kra.modules.report.dto.ReportResultResponse;
import com.company.kra.modules.report.registry.ReportRegistry;
import com.company.kra.security.AccessScopeResolver;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The rules that sit between a caller and a report: what they may run, which
 * filters count, and what happens when a result is too big to be useful.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceTest {

    @Mock private AccessScopeResolver accessScopeResolver;

    private final AtomicReference<ReportContext> lastContext = new AtomicReference<>();
    private ReportService service;
    private int rowsToProduce = 3;

    @BeforeEach
    void setUp() {
        ReportDefinition timesheet = new SimpleReport("employee-timesheet", "Employee timesheet",
                "One row per person per day.", "Work tracking", "WORKLOG_READ_TEAM",
                Set.of(ReportFilterKey.EMPLOYEE_ID, ReportFilterKey.FROM_DATE),
                context -> {
                    lastContext.set(context);
                    return ReportTable.of(List.of(ReportColumn.text("name", "Employee")), rows());
                });

        ReportDefinition restricted = new SimpleReport("top-performers", "Top performers",
                "Highest scores.", "Performance", "PERFORMANCE_READ_ALL",
                Set.of(ReportFilterKey.YEAR),
                context -> ReportTable.empty(List.of(ReportColumn.text("name", "Employee"))));

        ReportRegistry registry = new ReportRegistry(List.of(timesheet, restricted));
        service = new ReportService(registry, accessScopeResolver);
        ReflectionTestUtils.setField(service, "maxRows", 10);

        when(accessScopeResolver.visibleEmployeeIds(any(AppUserPrincipal.class)))
                .thenReturn(new AccessScopeResolver.Scope(false, Set.of(3L, 4L)));

        signIn(Set.of("REPORT_VIEW", "WORKLOG_READ_TEAM"));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("The catalogue lists only the reports the caller holds the permission for")
    void catalogueIsFilteredByPermission() {
        assertThat(service.catalogue()).extracting("key").containsExactly("employee-timesheet");
    }

    @Test
    @DisplayName("Running a report the caller may not run is refused, not silently empty")
    void runningWithoutPermissionIsRefused() {
        assertThatThrownBy(() -> service.run("top-performers", Map.of(), 0, 25))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("do not have access");
    }

    @Test
    @DisplayName("The caller's data scope reaches the report rather than being applied afterwards")
    void scopeIsPassedIntoTheReport() {
        service.run("employee-timesheet", Map.of(), 0, 25);

        assertThat(lastContext.get().scope().employeeIds()).containsExactlyInAnyOrder(3L, 4L);
        assertThat(lastContext.get().scope().organisationWide()).isFalse();
    }

    @Test
    @DisplayName("Filters the report did not declare are dropped")
    void undeclaredFiltersAreDropped() {
        service.run("employee-timesheet",
                Map.of("employeeId", "3", "ratingBandId", "9", "nonsense", "x"), 0, 25);

        ReportContext context = lastContext.get();
        assertThat(context.longValue(ReportFilterKey.EMPLOYEE_ID)).isEqualTo(3L);
        assertThat(context.raw(ReportFilterKey.RATING_BAND_ID)).isEmpty();
    }

    @Test
    @DisplayName("Filter names are accepted in either camelCase or the enum's own spelling")
    void filterNamesAreForgiving() {
        service.run("employee-timesheet", Map.of("FROM_DATE", "2026-08-01"), 0, 25);
        assertThat(lastContext.get().raw(ReportFilterKey.FROM_DATE)).contains("2026-08-01");

        service.run("employee-timesheet", Map.of("fromDate", "2026-09-01"), 0, 25);
        assertThat(lastContext.get().raw(ReportFilterKey.FROM_DATE)).contains("2026-09-01");
    }

    @Test
    @DisplayName("A blank filter is ignored rather than treated as a value")
    void blankFiltersAreIgnored() {
        Map<String, String> filters = new java.util.HashMap<>();
        filters.put("employeeId", "   ");
        service.run("employee-timesheet", filters, 0, 25);

        assertThat(lastContext.get().longValue(ReportFilterKey.EMPLOYEE_ID)).isNull();
    }

    @Test
    @DisplayName("Results are paged, and the page count reflects the whole result")
    void pagesTheResult() {
        rowsToProduce = 7;
        ReportResultResponse first = service.run("employee-timesheet", Map.of(), 0, 3);

        assertThat(first.rows()).hasSize(3);
        assertThat(first.totalRows()).isEqualTo(7);
        assertThat(first.totalPages()).isEqualTo(3);

        ReportResultResponse last = service.run("employee-timesheet", Map.of(), 2, 3);
        assertThat(last.rows()).hasSize(1);
    }

    @Test
    @DisplayName("A page past the end returns nothing rather than throwing")
    void pagePastTheEnd() {
        rowsToProduce = 2;
        assertThat(service.run("employee-timesheet", Map.of(), 5, 25).rows()).isEmpty();
    }

    @Test
    @DisplayName("A result over the cap is refused with advice, not quietly truncated")
    void oversizedResultIsRefused() {
        rowsToProduce = 11;

        assertThatThrownBy(() -> service.run("employee-timesheet", Map.of(), 0, 25))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("over the 10 limit")
                .hasMessageContaining("Narrow the date range");
    }

    @Test
    @DisplayName("Filters are described in words for the header of an exported file")
    void describesFiltersForExport() {
        Map<String, String> described = service.describe(service.require("employee-timesheet"),
                Map.of("employeeId", "3", "fromDate", "2026-08-01"));

        assertThat(described).containsEntry("Employee id", "3");
        assertThat(described).containsEntry("From date", "2026-08-01");
    }

    // ------------------------------------------------------------- fixtures

    private List<ReportRow> rows() {
        List<ReportRow> rows = new ArrayList<>();
        for (int index = 0; index < rowsToProduce; index++) {
            rows.add(ReportRow.of("Employee " + index));
        }
        return rows;
    }

    private void signIn(Set<String> permissions) {
        AppUserPrincipal principal = AppUserPrincipal.builder()
                .userId(1L)
                .employeeId(1L)
                .companyId(1L)
                .departmentId(1L)
                .username("manager")
                .email("manager@company.com")
                .displayName("Rahul Sharma")
                .passwordHash("hash")
                .enabled(true)
                .accountLocked(false)
                .mustChangePassword(false)
                .dataScope(DataScope.REPORTS)
                .roles(Set.of("REPORTING_MANAGER"))
                .permissions(permissions)
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Set.of()));
    }
}
