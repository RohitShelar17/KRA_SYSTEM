package com.company.kra.modules.performance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The scheduler lock and its history in one row. The unique key on
 * (job_name, run_key) is what stops two instances running the same job for
 * the same period.
 */
@Getter
@Setter
@Entity
@Table(name = "scheduled_job_runs")
public class ScheduledJobRun {

    public enum Status {
        RUNNING,
        SUCCESS,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 60)
    private String jobName;

    /** Identifies the period this run covers, e.g. 2026-09-06 or 2026-Q3. */
    @Column(name = "run_key", nullable = false, length = 40)
    private String runKey;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.RUNNING;

    @Column(name = "processed_count", nullable = false)
    private int processedCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(length = 500)
    private String message;
}
