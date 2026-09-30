package com.company.kra.modules.task.service;

import com.company.kra.common.enums.TaskStatus;
import com.company.kra.common.report.ReportColumn;
import com.company.kra.common.report.ReportRow;
import com.company.kra.common.report.ReportTable;
import com.company.kra.modules.task.repository.TaskRepository;
import com.company.kra.security.AccessScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskReportDataProvider implements TaskReportData {

    private static final Set<Long> NO_IDS = Set.of(-1L);

    private final TaskRepository taskRepository;

    @Override
    @Transactional(readOnly = true)
    public ReportTable taskCompletion(Long companyId, AccessScopeResolver.Scope scope, LocalDate from,
                                      LocalDate to, Long employeeId, Long departmentId,
                                      TaskStatus status) {
        List<ReportColumn> columns = List.of(
                ReportColumn.text("employeeCode", "Code"),
                ReportColumn.text("employeeName", "Employee"),
                ReportColumn.text("department", "Department"),
                ReportColumn.text("title", "Task"),
                ReportColumn.text("project", "Project"),
                ReportColumn.text("category", "Category"),
                ReportColumn.date("assignedDate", "Assigned"),
                ReportColumn.date("dueDate", "Due"),
                ReportColumn.date("completedOn", "Completed"),
                ReportColumn.status("status", "Status"),
                ReportColumn.text("priority", "Priority"),
                ReportColumn.duration("estimatedMinutes", "Estimate"),
                ReportColumn.duration("actualMinutes", "Actual"),
                ReportColumn.percent("completionPct", "Complete"),
                ReportColumn.text("timeliness", "Timeliness"));

        List<ReportRow> rows = taskRepository
                .reportTasks(companyId, scope.organisationWide(),
                        scope.organisationWide() || scope.employeeIds().isEmpty()
                                ? NO_IDS : scope.employeeIds(),
                        employeeId, departmentId, status, from, to).stream()
                .map(row -> {
                    LocalDate due = (LocalDate) row[7];
                    LocalDateTime completedAt = (LocalDateTime) row[8];
                    return ReportRow.of(row[0], row[1], row[2], row[3], row[4], row[5], row[6], due,
                            completedAt == null ? null : completedAt.toLocalDate(), row[9], row[10],
                            row[11], row[12], row[13], timeliness(due, completedAt, (TaskStatus) row[9]));
                })
                .toList();

        return ReportTable.of(columns, rows);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskReminder> dueOrOverdue(Long companyId, LocalDate today) {
        return taskRepository.findDueOrOverdue(companyId, today.plusDays(1)).stream()
                .map(row -> {
                    LocalDate due = (LocalDate) row[4];
                    return new TaskReminder(((Number) row[0]).longValue(), (String) row[1],
                            ((Number) row[2]).longValue(),
                            row[3] == null ? null : ((Number) row[3]).longValue(),
                            due, String.valueOf(row[5]).toLowerCase().replace('_', ' '),
                            due.isBefore(today));
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TaskCounts counts(Long companyId, AccessScopeResolver.Scope scope, LocalDate from,
                             LocalDate to, Long departmentId) {
        List<Object[]> rows = taskRepository.reportCounts(companyId, scope.organisationWide(),
                scope.organisationWide() || scope.employeeIds().isEmpty()
                        ? NO_IDS : scope.employeeIds(),
                departmentId, from, to, LocalDate.now());

        if (rows.isEmpty() || rows.get(0)[0] == null) {
            return new TaskCounts(0, 0, 0);
        }
        Object[] row = rows.get(0);
        return new TaskCounts(count(row[0]), count(row[1]), count(row[2]));
    }

    private long count(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /**
     * A task with no due date cannot be late, and one that is still open is
     * not "on time" yet — it is either running or already overdue.
     */
    private String timeliness(LocalDate due, LocalDateTime completedAt, TaskStatus status) {
        if (due == null) {
            return "No due date";
        }
        if (completedAt != null) {
            return completedAt.toLocalDate().isAfter(due) ? "Late" : "On time";
        }
        if (status == TaskStatus.CANCELLED) {
            return "Cancelled";
        }
        return LocalDate.now().isAfter(due) ? "Overdue" : "In flight";
    }
}
