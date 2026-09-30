package com.company.kra.scheduler;

import com.company.kra.common.enums.PerformanceScope;
import com.company.kra.exception.BusinessException;
import com.company.kra.modules.employee.entity.Employee;
import com.company.kra.modules.employee.repository.EmployeeRepository;
import com.company.kra.modules.organisation.repository.CompanyRepository;
import com.company.kra.modules.performance.entity.ScheduledJobRun;
import com.company.kra.modules.performance.repository.ScheduledJobRunRepository;
import com.company.kra.modules.performance.service.PerformanceCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The nightly and period-end score runs.
 *
 * <p>Each run claims a row in {@code scheduled_job_runs} keyed by job and
 * period. The unique constraint is the lock: a second instance trying to claim
 * the same key fails the insert and stands down, so two servers cannot both
 * score the same night.</p>
 *
 * <p>One employee failing does not stop the rest — each is scored in its own
 * transaction and failures are counted into the run record.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PerformanceJobs {

    private final ScheduledJobRunRepository runRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final PerformanceCalculationService calculationService;

    /** Yesterday's daily scores, once the day is definitely over. */
    @Scheduled(cron = "${app.jobs.daily-performance:0 15 1 * * *}", zone = "Asia/Kolkata")
    public void dailyPerformance() {
        LocalDate target = LocalDate.now().minusDays(1);
        run("DAILY_PERFORMANCE", target.toString(), (companyId, employeeId) ->
                calculationService.calculateDaily(companyId, employeeId, target));
    }

    /** The closed month, on the first of the next one. */
    @Scheduled(cron = "${app.jobs.monthly-performance:0 0 2 1 * *}", zone = "Asia/Kolkata")
    public void monthlyPerformance() {
        LocalDate previous = LocalDate.now().minusMonths(1);
        run("MONTHLY_PERFORMANCE", previous.getYear() + "-" + previous.getMonthValue(),
                (companyId, employeeId) -> calculationService.calculateMonthly(companyId, employeeId,
                        previous.getYear(), previous.getMonthValue()));
    }

    /** The closed quarter, on the first day of the next one. */
    @Scheduled(cron = "${app.jobs.quarterly-performance:0 30 2 1 1,4,7,10 *}", zone = "Asia/Kolkata")
    public void quarterlyPerformance() {
        LocalDate previous = LocalDate.now().minusMonths(1);
        int quarter = (previous.getMonthValue() - 1) / 3 + 1;
        run("QUARTERLY_PERFORMANCE", previous.getYear() + "-Q" + quarter,
                (companyId, employeeId) -> calculationService.calculateQuarterly(companyId, employeeId,
                        previous.getYear(), quarter));
    }

    /** The closed year, on 1 January. */
    @Scheduled(cron = "${app.jobs.annual-performance:0 0 3 1 1 *}", zone = "Asia/Kolkata")
    public void annualPerformance() {
        int year = LocalDate.now().minusMonths(1).getYear();
        run("ANNUAL_PERFORMANCE", String.valueOf(year),
                (companyId, employeeId) -> calculationService.calculateAnnual(companyId, employeeId, year));
    }

    // ------------------------------------------------------------ internals

    private void run(String jobName, String runKey, BiConsumer<Long, Long> work) {
        ScheduledJobRun claim = claim(jobName, runKey);
        if (claim == null) {
            log.info("{} for {} is already claimed elsewhere; standing down", jobName, runKey);
            return;
        }

        int processed = 0;
        int failed = 0;

        try {
            List<Long> companyIds = companyRepository.findAll().stream()
                    .map(company -> company.getId())
                    .toList();

            for (Long companyId : companyIds) {
                List<Employee> employees =
                        employeeRepository.findByCompanyIdAndActiveTrueOrderByFirstNameAsc(companyId);
                for (Employee employee : employees) {
                    try {
                        work.accept(companyId, employee.getId());
                        processed++;
                    } catch (BusinessException ex) {
                        // A frozen snapshot or a missing formula is expected in
                        // normal running, so it is counted, not shouted about.
                        log.debug("{} skipped employee {}: {}", jobName, employee.getId(),
                                ex.getMessage());
                        failed++;
                    } catch (RuntimeException ex) {
                        log.error("{} failed for employee {}", jobName, employee.getId(), ex);
                        failed++;
                    }
                }
            }
            complete(claim, ScheduledJobRun.Status.SUCCESS, processed, failed, null);
            log.info("{} for {} finished: {} scored, {} skipped or failed", jobName, runKey,
                    processed, failed);
        } catch (RuntimeException ex) {
            log.error("{} for {} aborted", jobName, runKey, ex);
            complete(claim, ScheduledJobRun.Status.FAILED, processed, failed, ex.getMessage());
        }
    }

    /**
     * Claims the run. Returns null when another instance already holds it —
     * the unique key on (job_name, run_key) does the arbitration.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected ScheduledJobRun claim(String jobName, String runKey) {
        if (runRepository.findByJobNameAndRunKey(jobName, runKey).isPresent()) {
            return null;
        }
        try {
            ScheduledJobRun run = new ScheduledJobRun();
            run.setJobName(jobName);
            run.setRunKey(runKey);
            run.setStartedAt(LocalDateTime.now());
            run.setStatus(ScheduledJobRun.Status.RUNNING);
            return runRepository.saveAndFlush(run);
        } catch (DataIntegrityViolationException ex) {
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void complete(ScheduledJobRun run, ScheduledJobRun.Status status, int processed,
                            int failed, String message) {
        run.setStatus(status);
        run.setProcessedCount(processed);
        run.setFailedCount(failed);
        run.setFinishedAt(LocalDateTime.now());
        run.setMessage(message == null ? null : message.substring(0, Math.min(message.length(), 500)));
        runRepository.save(run);
    }
}
