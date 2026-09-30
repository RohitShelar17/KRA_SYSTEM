-- =====================================================================
-- Phase 4 : Performance engine
--
-- Scores are snapshots, not formulas evaluated on demand. Every row records
-- the formula version that produced it and a JSON breakdown of each
-- parameter's raw value, weight and contribution, so a score can always be
-- explained in an appraisal conversation and cannot silently change when the
-- formula is edited later.
--
-- The formula itself is data: parameters and weights are rows, versioned as a
-- set, with exactly one active configuration per scope.
-- =====================================================================

CREATE TABLE performance_formula_config (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT       NOT NULL,
    scope_type      ENUM('DAILY','MONTHLY','QUARTERLY','ANNUAL') NOT NULL,
    version         INT          NOT NULL,
    name            VARCHAR(150) NOT NULL,
    notes           VARCHAR(500) NULL,
    -- Penalty applied when someone files too few work logs to trust the rest
    -- of the figures. Zero disables it.
    min_submission_rate_pct DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    submission_penalty_pct  DECIMAL(5,2) NOT NULL DEFAULT 0.00,
    is_active       TINYINT(1)   NOT NULL DEFAULT 0,
    effective_from  DATE         NOT NULL,
    created_at      DATETIME     NOT NULL,
    created_by      BIGINT       NULL,
    updated_at      DATETIME     NULL,
    updated_by      BIGINT       NULL,
    UNIQUE KEY uk_formula_scope_version (company_id, scope_type, version),
    KEY idx_formula_active (company_id, scope_type, is_active),
    CONSTRAINT fk_formula_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE performance_formula_parameters (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT       NOT NULL,
    config_id       BIGINT       NOT NULL,
    parameter_code  VARCHAR(40)  NOT NULL,
    label           VARCHAR(150) NOT NULL,
    weightage       DECIMAL(5,2) NOT NULL,
    display_order   INT          NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL,
    created_by      BIGINT       NULL,
    updated_at      DATETIME     NULL,
    updated_by      BIGINT       NULL,
    UNIQUE KEY uk_formula_param (config_id, parameter_code),
    CONSTRAINT fk_param_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_param_config  FOREIGN KEY (config_id) REFERENCES performance_formula_config (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Bands must be contiguous and non-overlapping across 0-100, so no score can
-- fall between two of them. Validated in the service layer.
CREATE TABLE performance_rating_bands (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT       NOT NULL,
    name            VARCHAR(60)  NOT NULL,
    description     VARCHAR(300) NULL,
    min_score       DECIMAL(5,2) NOT NULL,
    max_score       DECIMAL(5,2) NOT NULL,
    colour_hex      VARCHAR(7)   NULL,
    display_order   INT          NOT NULL DEFAULT 0,
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL,
    created_by      BIGINT       NULL,
    updated_at      DATETIME     NULL,
    updated_by      BIGINT       NULL,
    UNIQUE KEY uk_band_name (company_id, name),
    KEY idx_band_range (company_id, min_score, max_score),
    CONSTRAINT fk_band_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Snapshots. One row per employee per period, per grain.
-- ---------------------------------------------------------------------

CREATE TABLE daily_performance (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT   NOT NULL,
    employee_id             BIGINT   NOT NULL,
    perf_date               DATE     NOT NULL,
    tasks_assigned          INT      NOT NULL DEFAULT 0,
    tasks_completed         INT      NOT NULL DEFAULT 0,
    logged_minutes          INT      NOT NULL DEFAULT 0,
    productive_minutes      INT      NOT NULL DEFAULT 0,
    expected_minutes        INT      NOT NULL DEFAULT 0,
    task_completion_score   DECIMAL(5,2) NULL,
    kra_score               DECIMAL(5,2) NULL,
    productivity_score      DECIMAL(5,2) NULL,
    quality_score           DECIMAL(5,2) NULL,
    manager_rating          DECIMAL(5,2) NULL,
    overall_score           DECIMAL(5,2) NULL,
    rating_band_id          BIGINT   NULL,
    formula_version         INT      NOT NULL,
    score_breakdown         JSON     NULL,
    status                  ENUM('COMPUTED','SELF_ASSESSED','MANAGER_REVIEWED','HEAD_REVIEWED',
                                 'HR_REVIEWED','FINALIZED') NOT NULL DEFAULT 'COMPUTED',
    computed_at             DATETIME NOT NULL,
    finalized_at            DATETIME NULL,
    finalized_by            BIGINT   NULL,
    created_at              DATETIME NOT NULL,
    created_by              BIGINT   NULL,
    updated_at              DATETIME NULL,
    updated_by              BIGINT   NULL,
    UNIQUE KEY uk_daily_perf (employee_id, perf_date),
    KEY idx_daily_perf_date (perf_date),
    KEY idx_daily_perf_band (rating_band_id),
    CONSTRAINT fk_dp_company  FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_dp_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_dp_band     FOREIGN KEY (rating_band_id) REFERENCES performance_rating_bands (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE monthly_performance (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT   NOT NULL,
    employee_id             BIGINT   NOT NULL,
    perf_year               SMALLINT NOT NULL,
    perf_month              TINYINT  NOT NULL,
    period_start            DATE     NOT NULL,
    period_end              DATE     NOT NULL,
    tasks_assigned          INT      NOT NULL DEFAULT 0,
    tasks_completed         INT      NOT NULL DEFAULT 0,
    tasks_pending           INT      NOT NULL DEFAULT 0,
    logged_minutes          INT      NOT NULL DEFAULT 0,
    productive_minutes      INT      NOT NULL DEFAULT 0,
    expected_minutes        INT      NOT NULL DEFAULT 0,
    working_days            INT      NOT NULL DEFAULT 0,
    submitted_days          INT      NOT NULL DEFAULT 0,
    submission_rate_pct     DECIMAL(5,2) NULL,
    daily_average_score     DECIMAL(5,2) NULL,
    task_completion_score   DECIMAL(5,2) NULL,
    kra_score               DECIMAL(5,2) NULL,
    kpi_score               DECIMAL(5,2) NULL,
    productivity_score      DECIMAL(5,2) NULL,
    quality_score           DECIMAL(5,2) NULL,
    manager_rating          DECIMAL(5,2) NULL,
    overall_score           DECIMAL(5,2) NULL,
    rating_band_id          BIGINT   NULL,
    formula_version         INT      NOT NULL,
    score_breakdown         JSON     NULL,
    status                  ENUM('COMPUTED','SELF_ASSESSED','MANAGER_REVIEWED','HEAD_REVIEWED',
                                 'HR_REVIEWED','FINALIZED') NOT NULL DEFAULT 'COMPUTED',
    computed_at             DATETIME NOT NULL,
    finalized_at            DATETIME NULL,
    finalized_by            BIGINT   NULL,
    created_at              DATETIME NOT NULL,
    created_by              BIGINT   NULL,
    updated_at              DATETIME NULL,
    updated_by              BIGINT   NULL,
    UNIQUE KEY uk_monthly_perf (employee_id, perf_year, perf_month),
    KEY idx_monthly_perf_period (perf_year, perf_month, overall_score),
    CONSTRAINT fk_mp_company  FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_mp_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_mp_band     FOREIGN KEY (rating_band_id) REFERENCES performance_rating_bands (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE quarterly_performance (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT   NOT NULL,
    employee_id             BIGINT   NOT NULL,
    perf_year               SMALLINT NOT NULL,
    perf_quarter            TINYINT  NOT NULL,
    period_start            DATE     NOT NULL,
    period_end              DATE     NOT NULL,
    months_included         INT      NOT NULL DEFAULT 0,
    monthly_average_score   DECIMAL(5,2) NULL,
    kra_score               DECIMAL(5,2) NULL,
    kpi_score               DECIMAL(5,2) NULL,
    manager_rating          DECIMAL(5,2) NULL,
    overall_score           DECIMAL(5,2) NULL,
    rating_band_id          BIGINT   NULL,
    formula_version         INT      NOT NULL,
    score_breakdown         JSON     NULL,
    status                  ENUM('COMPUTED','SELF_ASSESSED','MANAGER_REVIEWED','HEAD_REVIEWED',
                                 'HR_REVIEWED','FINALIZED') NOT NULL DEFAULT 'COMPUTED',
    computed_at             DATETIME NOT NULL,
    finalized_at            DATETIME NULL,
    finalized_by            BIGINT   NULL,
    created_at              DATETIME NOT NULL,
    created_by              BIGINT   NULL,
    updated_at              DATETIME NULL,
    updated_by              BIGINT   NULL,
    UNIQUE KEY uk_quarterly_perf (employee_id, perf_year, perf_quarter),
    KEY idx_quarterly_perf_period (perf_year, perf_quarter, overall_score),
    CONSTRAINT fk_qp_company  FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_qp_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_qp_band     FOREIGN KEY (rating_band_id) REFERENCES performance_rating_bands (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE annual_performance (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT   NOT NULL,
    employee_id             BIGINT   NOT NULL,
    perf_year               SMALLINT NOT NULL,
    period_start            DATE     NOT NULL,
    period_end              DATE     NOT NULL,
    quarters_included       INT      NOT NULL DEFAULT 0,
    quarterly_average_score DECIMAL(5,2) NULL,
    kra_score               DECIMAL(5,2) NULL,
    kpi_score               DECIMAL(5,2) NULL,
    manager_rating          DECIMAL(5,2) NULL,
    department_head_rating  DECIMAL(5,2) NULL,
    self_assessment_score   DECIMAL(5,2) NULL,
    overall_score           DECIMAL(5,2) NULL,
    rating_band_id          BIGINT   NULL,
    formula_version         INT      NOT NULL,
    score_breakdown         JSON     NULL,
    status                  ENUM('COMPUTED','SELF_ASSESSED','MANAGER_REVIEWED','HEAD_REVIEWED',
                                 'HR_REVIEWED','FINALIZED') NOT NULL DEFAULT 'COMPUTED',
    computed_at             DATETIME NOT NULL,
    finalized_at            DATETIME NULL,
    finalized_by            BIGINT   NULL,
    created_at              DATETIME NOT NULL,
    created_by              BIGINT   NULL,
    updated_at              DATETIME NULL,
    updated_by              BIGINT   NULL,
    UNIQUE KEY uk_annual_perf (employee_id, perf_year),
    KEY idx_annual_perf_period (perf_year, overall_score),
    CONSTRAINT fk_ap_company  FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_ap_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_ap_band     FOREIGN KEY (rating_band_id) REFERENCES performance_rating_bands (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Every manual score change, with what it was, what it became and why.
CREATE TABLE performance_adjustments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    snapshot_type       ENUM('DAILY','MONTHLY','QUARTERLY','ANNUAL') NOT NULL,
    snapshot_id         BIGINT       NOT NULL,
    employee_id         BIGINT       NOT NULL,
    previous_score      DECIMAL(5,2) NULL,
    new_score           DECIMAL(5,2) NOT NULL,
    reason              VARCHAR(500) NOT NULL,
    adjusted_by         BIGINT       NULL,
    adjusted_by_name    VARCHAR(150) NULL,
    created_at          DATETIME     NOT NULL,
    KEY idx_adjustment_snapshot (snapshot_type, snapshot_id),
    KEY idx_adjustment_employee (employee_id, created_at),
    CONSTRAINT fk_adj_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Scheduler guard: the unique key is the lock, so two instances running the
-- same job for the same period cannot both proceed.
CREATE TABLE scheduled_job_runs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_name        VARCHAR(60)  NOT NULL,
    run_key         VARCHAR(40)  NOT NULL,
    started_at      DATETIME     NOT NULL,
    finished_at     DATETIME     NULL,
    status          ENUM('RUNNING','SUCCESS','FAILED') NOT NULL DEFAULT 'RUNNING',
    processed_count INT          NOT NULL DEFAULT 0,
    failed_count    INT          NOT NULL DEFAULT 0,
    message         VARCHAR(500) NULL,
    UNIQUE KEY uk_job_run (job_name, run_key),
    KEY idx_job_started (job_name, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
