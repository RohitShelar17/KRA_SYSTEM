package com.company.kra.modules.performance.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * How the scheduled scoring jobs have been getting on.
 *
 * <p>The administrator's dashboard shows this because a scheduler that stopped
 * running is invisible until somebody notices their score never appeared.</p>
 */
public interface JobHealthLookup {

    List<JobRun> recentRuns(Long companyId, int limit);

    record JobRun(String jobName, String periodKey, String status, int processed, int failed,
                  String message, LocalDateTime startedAt, LocalDateTime finishedAt) {

        public boolean healthy() {
            return "SUCCESS".equalsIgnoreCase(status) && failed == 0;
        }
    }
}
