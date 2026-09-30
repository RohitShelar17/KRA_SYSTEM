package com.company.kra.modules.performance.service;

import com.company.kra.common.enums.PerformanceScope;
import com.company.kra.common.report.ReportColumn;
import com.company.kra.common.report.ReportRow;
import com.company.kra.common.report.ReportTable;
import com.company.kra.exception.BusinessException;
import com.company.kra.exception.ErrorCode;
import com.company.kra.modules.performance.repository.AnnualPerformanceRepository;
import com.company.kra.modules.performance.repository.MonthlyPerformanceRepository;
import com.company.kra.modules.performance.repository.QuarterlyPerformanceRepository;
import com.company.kra.security.AccessScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PerformanceReportDataProvider implements PerformanceReportData {

    private static final Set<Long> NO_IDS = Set.of(-1L);

    private final MonthlyPerformanceRepository monthlyRepository;
    private final QuarterlyPerformanceRepository quarterlyRepository;
    private final AnnualPerformanceRepository annualRepository;
    private final RatingBandService bandService;

    @Override
    @Transactional(readOnly = true)
    public ReportTable snapshots(Long companyId, AccessScopeResolver.Scope scope,
                                 PerformanceScope grain, int year, Integer periodIndex,
                                 Long employeeId, Long departmentId, Long bandId) {
        return switch (grain) {
            case MONTHLY -> monthly(companyId, scope, year, periodIndex, employeeId, departmentId, bandId);
            case QUARTERLY -> quarterly(companyId, scope, year, periodIndex, employeeId, departmentId, bandId);
            case ANNUAL -> annual(companyId, scope, year, employeeId, departmentId, bandId);
            case DAILY -> throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Daily scores are reported through the timesheet reports, not this one");
        };
    }

    private ReportTable monthly(Long companyId, AccessScopeResolver.Scope scope, int year,
                                Integer month, Long employeeId, Long departmentId, Long bandId) {
        List<ReportColumn> columns = List.of(
                ReportColumn.text("employeeCode", "Code"),
                ReportColumn.text("employeeName", "Employee"),
                ReportColumn.text("department", "Department"),
                ReportColumn.text("designation", "Designation"),
                ReportColumn.text("period", "Period"),
                ReportColumn.score("overallScore", "Score"),
                ReportColumn.text("band", "Band"),
                ReportColumn.status("status", "Status"),
                ReportColumn.number("formulaVersion", "Formula"),
                ReportColumn.score("taskCompletion", "Tasks"),
                ReportColumn.score("kra", "KRA"),
                ReportColumn.score("kpi", "KPI"),
                ReportColumn.score("productivity", "Productivity"),
                ReportColumn.score("quality", "Quality"),
                ReportColumn.score("managerRating", "Manager"));

        List<ReportRow> rows = monthlyRepository
                .reportRows(companyId, scope.organisationWide(), ids(scope), (short) year,
                        month == null ? null : month.byteValue(), employeeId, departmentId, bandId)
                .stream()
                .map(row -> ReportRow.of(row[0], row[1], row[2], row[3],
                        monthLabel(number(row[5]), number(row[4])), row[6], band(row[9]), row[7],
                        row[8], row[10], row[11], row[12], row[13], row[14], row[15]))
                .toList();

        return ReportTable.of(columns, rows);
    }

    private ReportTable quarterly(Long companyId, AccessScopeResolver.Scope scope, int year,
                                  Integer quarter, Long employeeId, Long departmentId, Long bandId) {
        List<ReportColumn> columns = List.of(
                ReportColumn.text("employeeCode", "Code"),
                ReportColumn.text("employeeName", "Employee"),
                ReportColumn.text("department", "Department"),
                ReportColumn.text("designation", "Designation"),
                ReportColumn.text("period", "Period"),
                ReportColumn.score("overallScore", "Score"),
                ReportColumn.text("band", "Band"),
                ReportColumn.status("status", "Status"),
                ReportColumn.number("formulaVersion", "Formula"),
                ReportColumn.score("monthlyAverage", "Monthly average"),
                ReportColumn.number("monthsIncluded", "Months counted"));

        List<ReportRow> rows = quarterlyRepository
                .reportRows(companyId, scope.organisationWide(), ids(scope), (short) year,
                        quarter == null ? null : quarter.byteValue(), employeeId, departmentId, bandId)
                .stream()
                .map(row -> ReportRow.of(row[0], row[1], row[2], row[3],
                        "Q" + number(row[5]) + " " + number(row[4]), row[6], band(row[9]), row[7],
                        row[8], row[10], row[11]))
                .toList();

        return ReportTable.of(columns, rows);
    }

    private ReportTable annual(Long companyId, AccessScopeResolver.Scope scope, int year,
                               Long employeeId, Long departmentId, Long bandId) {
        List<ReportColumn> columns = List.of(
                ReportColumn.text("employeeCode", "Code"),
                ReportColumn.text("employeeName", "Employee"),
                ReportColumn.text("department", "Department"),
                ReportColumn.text("designation", "Designation"),
                ReportColumn.text("period", "Year"),
                ReportColumn.score("overallScore", "Score"),
                ReportColumn.text("band", "Band"),
                ReportColumn.status("status", "Status"),
                ReportColumn.number("formulaVersion", "Formula"),
                ReportColumn.score("quarterlyAverage", "Quarterly average"),
                ReportColumn.number("quartersIncluded", "Quarters counted"));

        List<ReportRow> rows = annualRepository
                .reportRows(companyId, scope.organisationWide(), ids(scope), (short) year,
                        employeeId, departmentId, bandId)
                .stream()
                .map(row -> ReportRow.of(row[0], row[1], row[2], row[3], String.valueOf(number(row[4])),
                        row[5], band(row[8]), row[6], row[7], row[9], row[10]))
                .toList();

        return ReportTable.of(columns, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportTable departmentPerformance(Long companyId, AccessScopeResolver.Scope scope,
                                             int year, Integer month) {
        List<ReportColumn> columns = List.of(
                ReportColumn.text("department", "Department"),
                ReportColumn.number("scored", "Scores counted"),
                ReportColumn.score("average", "Average"),
                ReportColumn.score("lowest", "Lowest"),
                ReportColumn.score("highest", "Highest"),
                ReportColumn.score("kra", "KRA average"),
                ReportColumn.score("kpi", "KPI average"),
                ReportColumn.score("productivity", "Productivity average"));

        List<ReportRow> rows = monthlyRepository
                .reportDepartmentAverages(companyId, scope.organisationWide(), ids(scope),
                        (short) year, month == null ? null : month.byteValue())
                .stream()
                .map(row -> ReportRow.of(row[0], row[1], round(row[2]), round(row[3]), round(row[4]),
                        round(row[5]), round(row[6]), round(row[7])))
                .toList();

        return ReportTable.of(columns, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportTable ranking(Long companyId, AccessScopeResolver.Scope scope, int year,
                               Integer month, Long departmentId, boolean lowestFirst, int limit) {
        List<ReportColumn> columns = List.of(
                ReportColumn.number("rank", "Rank"),
                ReportColumn.text("employeeCode", "Code"),
                ReportColumn.text("employeeName", "Employee"),
                ReportColumn.text("department", "Department"),
                ReportColumn.text("designation", "Designation"),
                ReportColumn.text("period", "Period"),
                ReportColumn.score("overallScore", "Score"),
                ReportColumn.text("band", "Band"),
                ReportColumn.status("status", "Status"));

        List<Object[]> raw = monthlyRepository.reportRanking(companyId, scope.organisationWide(),
                ids(scope), (short) year, month == null ? null : month.byteValue(), departmentId,
                lowestFirst, PageRequest.of(0, Math.min(Math.max(limit, 1), 200)));

        List<ReportRow> rows = new java.util.ArrayList<>();
        int position = 1;
        for (Object[] row : raw) {
            rows.add(ReportRow.of(position++, row[0], row[1], row[2], row[3],
                    monthLabel(number(row[7]), number(row[6])), row[4], band(row[5]), row[8]));
        }
        return ReportTable.of(columns, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BandCount> bandDistribution(Long companyId, AccessScopeResolver.Scope scope,
                                            int year, Integer month, Long departmentId) {
        return monthlyRepository.reportBandDistribution(companyId, scope.organisationWide(),
                        ids(scope), (short) year, month == null ? null : month.byteValue(),
                        departmentId)
                .stream()
                .map(row -> {
                    Long bandId = ((Number) row[0]).longValue();
                    return bandService.byId(bandId)
                            .map(band -> new BandCount(bandId, band.getName(), band.getColourHex(),
                                    ((Number) row[1]).longValue()))
                            .orElseGet(() -> new BandCount(bandId, "Unknown band", null,
                                    ((Number) row[1]).longValue()));
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AverageScore averageScore(Long companyId, AccessScopeResolver.Scope scope, int year,
                                     Integer month, Long departmentId) {
        List<Object[]> rows = monthlyRepository.reportAverageScore(companyId,
                scope.organisationWide(), ids(scope), (short) year,
                month == null ? null : month.byteValue(), departmentId);

        if (rows.isEmpty() || rows.get(0)[0] == null) {
            // No scores is not an average of zero; the caller shows "no data".
            return new AverageScore(null, 0);
        }
        Object[] row = rows.get(0);
        return new AverageScore(round(row[0]), ((Number) row[1]).longValue());
    }

    // ------------------------------------------------------------- internals

    private Set<Long> ids(AccessScopeResolver.Scope scope) {
        return scope.organisationWide() || scope.employeeIds().isEmpty()
                ? NO_IDS : scope.employeeIds();
    }

    private String band(Object bandId) {
        return bandId instanceof Number id ? bandService.nameOf(id.longValue()) : null;
    }

    private int number(Object value) {
        return value instanceof Number figure ? figure.intValue() : 0;
    }

    private String monthLabel(int month, int year) {
        return month < 1 || month > 12 ? String.valueOf(year)
                : Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + year;
    }

    /** Averages come back with database precision; two decimals is what a person reads. */
    private BigDecimal round(Object value) {
        if (!(value instanceof Number figure)) {
            return null;
        }
        return BigDecimal.valueOf(figure.doubleValue()).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
