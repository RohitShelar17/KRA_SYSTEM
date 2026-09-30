package com.company.kra.modules.performance.repository;

import com.company.kra.modules.performance.entity.ScheduledJobRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScheduledJobRunRepository extends JpaRepository<ScheduledJobRun, Long> {

    Optional<ScheduledJobRun> findByJobNameAndRunKey(String jobName, String runKey);

    List<ScheduledJobRun> findTop20ByOrderByStartedAtDesc();
}
