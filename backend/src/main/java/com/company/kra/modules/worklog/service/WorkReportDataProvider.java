package com.company.kra.modules.worklog.service;

import com.company.kra.common.report.ReportColumn;
import com.company.kra.common.report.ReportRow;
import com.company.kra.common.report.ReportTable;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.worklog.repository.DailyWorkLogRepository;
import com.company.kra.modules.worklog.repository.WorkLogDayRepository;
import com.company.kra.security.AccessScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The timesheet module's answer to the reporting module's questions.
 *
 * <p>Each query applies the caller's scope itself. Filtering after the fact
 * would still have pulled the rows out of the database, and a mistake in that
 * second step would be a data leak rather than a wrong total.</p>
 */
@Service
@RequiredArgsConstructor
public class WorkReportDataProvider implements WorkReportData {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Set<Long> NO_IDS = Set.of(-1L);

    private final DailyWorkLogRepository entryRepository;
    private final WorkLogDayRepository dayRepository;
    private final EmployeeRepository employeeRepository;
    private final WorkingCalendar workingCalendar;

    @Override
    @Transactional(readOnly = true)
    public ReportTable dailyWork(Long companyId, AccessScopeResolver.Scope scope, LocalDate from,
                                 LocalDate to, Long employeeId, Long departmentId) {
        List<ReportColumn> columns = List.of(
                ReportColumn.date("workDate", "Date"),
                ReportColumn.text("employeeCode", "Code"),
                ReportColumn.text("employeeName", "Employee"),
                ReportColumn.text("department", "Department"),
                ReportColumn.text("startTime", "From"),
                ReportColumn.text("endTime", "To"),
                ReportColumn.duration("durationMinutes", "Duration"),
                ReportColumn.text("category", "Category"),
                ReportColumn.text("title", "Work"),
                ReportColumn.status("status", "Status"),
                ReportColumn.percent("completionPct", "Complete"),
                ReportColumn.text("productive", "Productive"));

        List<ReportRow> rows = entryRepository
                .reportEntries(companyId, scope.organisationWide(), ids(scope), from, to,
                        employeeId, departmentId).stream()
                .map(row -> ReportRow.of(row[0], row[1], row[2], row[3], text(row[4]), text(row[5]),
                        row[6], row[7], row[8], row[9], row[10], yesNo(row[11])))
                .toList();

        return ReportTable.of(columns, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportTable employeeTimesheet(Long companyId, AccessScopeResolver.Scope scope,
                                         LocalDate from, LocalDate to, Long employeeId,
                                         Long departmentId) {
        List<ReportColumn> columns = List.of(
                ReportColumn.date("workDate", "Date"),
                ReportColumn.text("employeeCode", "Code"),
                ReportColumn.text("employeeName", "Employee"),
                ReportColumn.text("department", "Department"),
                ReportColumn.duration("officeMinutes", "Office"),
                ReportColumn.duration("loggedMinutes", "Logged"),
                ReportColumn.duration("productiveMinutes", "Productive"),
                ReportColumn.duration("unallocatedMinutes", "Unallocated"),
                ReportColumn.duration("overtimeMinutes", "Overtime"),
                ReportColumn.number("entryCount", "Entries"),
                ReportColumn.status("status", "Status"));

        List<ReportRow> rows = dayRepository
                .reportTimesheet(companyId, scope.organisationWide(), ids(scope), from, to,
                        employeeId, departmentId).stream()
                .map(row -> ReportRow.of(row[0], row[1], row[2], row[3], row[4], row[5], row[6],
                        row[7], row[8], row[9], row[10]))
                .toList();

        return ReportTable.of(columns, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportTable departmentTimesheet(Long companyId, AccessScopeResolver.Scope scope,
                                           LocalDate from, LocalDate to, Long departmentId) {
        List<ReportColumn> columns = List.of(
                ReportColumn.text("department", "Department"),
                ReportColumn.number("employees", "People"),
                ReportColumn.number("daysLogged", "Days logged"),
                ReportColumn.duration("loggedMinutes", "Logged"),
                ReportColumn.duration("productiveMinutes", "Productive"),
                ReportColumn.percent("productiveRatio", "Productive %"),
                ReportColumn.duration("unallocatedMinutes", "Unallocated"),
                ReportColumn.duration("overtimeMinutes", "Overtime"));

        List<ReportRow> rows = dayRepository
                .reportDepartmentTimesheet(companyId, scope.organisationWide(), ids(scope), from, to,
                        departmentId).stream()
                .map(row -> {
                    long logged = number(row[3]);
                    long productive = number(row[4]);
                    return ReportRow.of(row[0], row[1], row[2], logged, productive,
                            ratio(productive, logged), row[5], row[6]);
                })
                .toList();

        return ReportTable.of(columns, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportTable productivity(Long companyId, AccessScopeResolver.Scope scope, LocalDate from,
                                    LocalDate to, Long employeeId, Long departmentId) {
        List<ReportColumn> columns = List.of(
                ReportColumn.text("employeeCode", "Code"),
                ReportColumn.text("employeeName", "Employee"),
                ReportColumn.text("department", "Department"),
                ReportColumn.number("daysLogged", "Days logged"),
                ReportColumn.duration("loggedMinutes", "Logged"),
                ReportColumn.duration("productiveMinutes", "Productive"),
                ReportColumn.duration("meetingMinutes", "Meetings"),
                ReportColumn.duration("breakMinutes", "Breaks"),
                ReportColumn.percent("productiveRatio", "Productive %"));

        List<ReportRow> rows = dayRepository
                .reportProductivity(companyId, scope.organisationWide(), ids(scope), from, to,
                        employeeId, departmentId).stream()
                .map(row -> {
                    long logged = number(row[4]);
                    long productive = number(row[5]);
                    return ReportRow.of(row[0], row[1], row[2], row[3], logged, productive, row[6],
                            row[7], ratio(productive, logged));
                })
                .toList();

        return ReportTable.of(columns, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportTable pendingWorkLogs(Long companyId, AccessScopeResolver.Scope scope,
                                       LocalDate from, LocalDate to, Long departmentId) {
        List<ReportColumn> columns = List.of(
                ReportColumn.text("employeeCode", "Code"),
                ReportColumn.text("employeeName", "Employee"),
                ReportColumn.text("department", "Department"),
                ReportColumn.text("manager", "Reports to"),
                ReportColumn.number("workingDays", "Working days"),
                ReportColumn.number("filed", "Filed"),
                ReportColumn.number("missing", "Missing"),
                ReportColumn.text("missingDates", "Dates"));

        // Yesterday at the latest: today is still open, and chasing somebody
        // for a day that has not finished is how a report loses its authority.
        LocalDate end = to.isBefore(LocalDate.now()) ? to : LocalDate.now().minusDays(1);
        List<LocalDate> workingDays = workingCalendar.workingDaysBetween(companyId, from, end);
        if (workingDays.isEmpty()) {
            return ReportTable.empty(columns);
        }

        Map<Long, Set<LocalDate>> filed = new HashMap<>();
        for (Object[] row : dayRepository.filedDaysBetween(companyId, from, end)) {
            filed.computeIfAbsent((Long) row[0], key -> new HashSet<>()).add((LocalDate) row[1]);
        }

        List<ReportRow> rows = new ArrayList<>();
        for (Employee employee : employeeRepository
                .findByCompanyIdAndActiveTrueOrderByFirstNameAsc(companyId)) {
            if (!scope.covers(employee.getId())) {
                continue;
            }
            if (departmentId != null && (employee.getDepartment() == null
                    || !departmentId.equals(employee.getDepartment().getId()))) {
                continue;
            }

            Set<LocalDate> theirs = filed.getOrDefault(employee.getId(), Set.of());

            // Somebody who joined mid-window is measured against the days they
            // were actually here for, not the whole window.
            List<LocalDate> expected = workingDays.stream()
                    .filter(date -> employee.getJoiningDate() == null
                            || !date.isBefore(employee.getJoiningDate()))
                    .toList();
            List<LocalDate> missing = expected.stream()
                    .filter(date -> !theirs.contains(date))
                    .toList();

            if (missing.isEmpty()) {
                continue;
            }
            rows.add(ReportRow.of(employee.getEmployeeCode(), employee.fullName(),
                    employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                    employee.getReportingManager() == null ? null
                            : employee.getReportingManager().fullName(),
                    expected.size(), expected.size() - missing.size(), missing.size(),
                    summarise(missing)));
        }

        return ReportTable.of(columns, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> employeesWithoutLog(Long companyId, LocalDate date) {
        if (workingCalendar.workingDaysBetween(companyId, date, date).isEmpty()) {
            // Not a working day: nobody is late.
            return Set.of();
        }
        Set<Long> filed = dayRepository.findEmployeeIdsWithSubmissionOn(date);
        Set<Long> missing = new HashSet<>();
        for (Employee employee : employeeRepository
                .findByCompanyIdAndActiveTrueOrderByFirstNameAsc(companyId)) {
            boolean joinedAlready = employee.getJoiningDate() == null
                    || !date.isBefore(employee.getJoiningDate());
            if (joinedAlready && !filed.contains(employee.getId())) {
                missing.add(employee.getId());
            }
        }
        return missing;
    }

    // ------------------------------------------------------------- internals

    private Set<Long> ids(AccessScopeResolver.Scope scope) {
        // An empty IN list is invalid SQL; the sentinel keeps the query legal
        // and matches nothing, which is the right answer for an empty scope.
        return scope.organisationWide() || scope.employeeIds().isEmpty()
                ? NO_IDS : scope.employeeIds();
    }

    private String summarise(List<LocalDate> dates) {
        int shown = Math.min(dates.size(), 8);
        String list = dates.stream().limit(shown).map(LocalDate::toString)
                .reduce((a, b) -> a + ", " + b).orElse("");
        return dates.size() > shown ? list + " +" + (dates.size() - shown) + " more" : list;
    }

    private BigDecimal ratio(long part, long whole) {
        return whole == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(part).multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }

    private long number(Object value) {
        return value instanceof Number figure ? figure.longValue() : 0L;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String yesNo(Object value) {
        return Boolean.TRUE.equals(value) ? "Yes" : "No";
    }
}
