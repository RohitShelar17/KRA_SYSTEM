package com.company.kra.scheduler;

import com.company.kra.common.events.NotificationRaised;
import com.company.kra.modules.appraisal.service.AppraisalReportData;
import com.company.kra.modules.employee.service.EmployeeDirectory;
import com.company.kra.modules.notification.service.NotificationService;
import com.company.kra.modules.organisation.entity.Company;
import com.company.kra.modules.organisation.repository.CompanyRepository;
import com.company.kra.modules.task.service.TaskReportData;
import com.company.kra.modules.worklog.service.WorkReportData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/**
 * The nightly nudges: unfiled days, tasks about to bite, reviews past their
 * service level.
 *
 * <p>Two rules keep these from becoming noise people learn to ignore. Nothing
 * is sent twice in a day — {@link NotificationService#alreadyToldToday} is
 * checked before every send — and nothing is sent about a day that is not a
 * working day or a step with no configured SLA.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderJobs {

    private final WorkReportData workData;
    private final TaskReportData taskData;
    private final AppraisalReportData appraisalData;
    private final NotificationService notificationService;
    private final EmployeeDirectory employeeDirectory;
    private final CompanyRepository companyRepository;

    /** Yesterday's unfiled days, the morning after. */
    @Scheduled(cron = "${app.jobs.missing-worklog-reminder:0 30 9 * * *}", zone = "Asia/Kolkata")
    public void remindMissingWorkLogs() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        for (Company company : companyRepository.findAll()) {
            Set<Long> missing = workData.employeesWithoutLog(company.getId(), yesterday);
            int sent = 0;

            for (Long employeeId : missing) {
                if (told(company.getId(), employeeId, "WORKLOG_MISSING", "WorkLogDay",
                        yesterday.toEpochDay())) {
                    continue;
                }
                notificationService.dispatch(NotificationRaised
                        .to(company.getId(), "WORKLOG_MISSING")
                        .recipient(employeeId)
                        .about("WorkLogDay", yesterday.toEpochDay())
                        .with("workDate", yesterday)
                        .build());
                sent++;
            }
            log.info("Work-log reminder for {}: {} of {} people told", yesterday, sent,
                    missing.size());
        }
    }

    /** Tasks due tomorrow, and ones already past their date. */
    @Scheduled(cron = "${app.jobs.task-reminder:0 0 10 * * *}", zone = "Asia/Kolkata")
    public void remindTasks() {
        LocalDate today = LocalDate.now();

        for (Company company : companyRepository.findAll()) {
            for (TaskReportData.TaskReminder task : taskData.dueOrOverdue(company.getId(), today)) {
                String eventKey = task.overdue() ? "TASK_OVERDUE" : "TASK_DUE_SOON";
                if (told(company.getId(), task.assigneeId(), eventKey, "Task", task.taskId())) {
                    continue;
                }

                NotificationRaised.Builder event = NotificationRaised.to(company.getId(), eventKey)
                        .recipient(task.assigneeId())
                        .about("Task", task.taskId())
                        .with("taskTitle", task.title())
                        .with("dueDate", task.dueDate())
                        .with("status", task.status());

                // A manager hears about a task that has slipped, not about one
                // that is merely due tomorrow.
                if (task.overdue()) {
                    event.recipient(task.managerId());
                }
                notificationService.dispatch(event.build());
            }
        }
    }

    /** Reviews sitting longer than the workflow says they should. */
    @Scheduled(cron = "${app.jobs.review-sla-reminder:0 30 10 * * *}", zone = "Asia/Kolkata")
    public void remindOverdueReviews() {
        LocalDate today = LocalDate.now();

        for (Company company : companyRepository.findAll()) {
            for (AppraisalReportData.ReviewReminder review
                    : appraisalData.overdueReviews(company.getId(), today)) {
                if (review.reviewerId() == null) {
                    // A step owned by a permission rather than a person shows in
                    // the queue; there is nobody specific to chase.
                    continue;
                }
                if (told(company.getId(), review.reviewerId(), "REVIEW_OVERDUE", "AppraisalRecord",
                        review.appraisalId())) {
                    continue;
                }
                notificationService.dispatch(NotificationRaised
                        .to(company.getId(), "REVIEW_OVERDUE")
                        .recipient(review.reviewerId())
                        .about("AppraisalRecord", review.appraisalId())
                        .with("employeeName", review.employeeName())
                        .with("stepName", review.stepName())
                        .with("slaDays", review.slaDays())
                        .with("daysWaiting", review.daysWaiting())
                        .with("periodLabel", review.periodLabel())
                        .build());
            }
        }
    }

    /** True when this person already heard this today. */
    private boolean told(Long companyId, Long employeeId, String eventKey, String entityType,
                         Long entityId) {
        if (employeeId == null) {
            return true;
        }
        Map<Long, EmployeeDirectory.Account> accounts =
                employeeDirectory.accountsOf(Set.of(employeeId));
        EmployeeDirectory.Account account = accounts.get(employeeId);
        return account == null
                || notificationService.alreadyToldToday(account.userId(), eventKey, entityType,
                        entityId);
    }
}
