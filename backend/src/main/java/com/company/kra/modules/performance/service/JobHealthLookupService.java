package com.company.kra.modules.performance.service;

import com.company.kra.modules.performance.repository.ScheduledJobRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reads the scheduler's own record of its runs.
 *
 * <p>Job rows are not company-scoped — the scheduler runs per deployment — so
 * the company id is accepted for symmetry with the other lookups and ignored
 * here rather than pretending to filter by it.</p>
 */
@Service
@RequiredArgsConstructor
public class JobHealthLookupService implements JobHealthLookup {

    private final ScheduledJobRunRepository runRepository;

    @Override
    @Transactional(readOnly = true)
    public List<JobRun> recentRuns(Long companyId, int limit) {
        return runRepository.findTop20ByOrderByStartedAtDesc().stream()
                .limit(Math.max(limit, 1))
                .map(row -> new JobRun(row.getJobName(), row.getRunKey(), row.getStatus().name(),
                        row.getProcessedCount(), row.getFailedCount(), row.getMessage(),
                        row.getStartedAt(), row.getFinishedAt()))
                .toList();
    }
}
