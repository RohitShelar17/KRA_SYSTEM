package com.company.kra.modules.task.service;

import com.company.kra.common.metrics.TaskMetrics;
import com.company.kra.modules.task.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Supplies delivery figures measured from the task board. */
@Service
@RequiredArgsConstructor
public class TaskMetricsProvider implements TaskMetrics {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final TaskRepository taskRepository;

    @Override
    @Transactional(readOnly = true)
    public TaskSummary summarise(Long employeeId, LocalDate from, LocalDate to) {
        return new TaskSummary(
                taskRepository.countAssignedBetween(employeeId, from, to),
                taskRepository.countCompletedBetween(employeeId, from, to),
                taskRepository.countCompletedOnTimeBetween(employeeId, from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal completionRatePercent(Long employeeId, LocalDate from, LocalDate to) {
        long assigned = taskRepository.countAssignedBetween(employeeId, from, to);
        if (assigned == 0) {
            // Nobody assigned anything: not a failure, just nothing to measure.
            return null;
        }
        return ratio(taskRepository.countCompletedBetween(employeeId, from, to), assigned);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal onTimeRatePercent(Long employeeId, LocalDate from, LocalDate to) {
        long completed = taskRepository.countCompletedBetween(employeeId, from, to);
        if (completed == 0) {
            return null;
        }
        return ratio(taskRepository.countCompletedOnTimeBetween(employeeId, from, to), completed);
    }

    private BigDecimal ratio(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
