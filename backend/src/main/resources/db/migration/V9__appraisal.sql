-- =====================================================================
-- Phase 5 : Appraisal
--
-- A cycle opens the windows. Each employee in it gets one appraisal record,
-- which is the thing that moves through the workflow; the self assessment and
-- the reviews hang off it.
--
-- The workflow itself is configuration: an ordered list of steps, each
-- optional or required, so an organisation that does not do department-head
-- sign-off simply does not configure that step.
-- =====================================================================

CREATE TABLE review_workflow_steps (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT       NOT NULL,
    step_order      INT          NOT NULL,
    step_type       ENUM('SELF_ASSESSMENT','MANAGER_REVIEW','DEPARTMENT_HEAD_REVIEW','HR_REVIEW')
                    NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(400) NULL,
    is_required     TINYINT(1)   NOT NULL DEFAULT 1,
    sla_days        INT          NULL,
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL,
    created_by      BIGINT       NULL,
    updated_at      DATETIME     NULL,
    updated_by      BIGINT       NULL,
    UNIQUE KEY uk_workflow_step (company_id, step_type),
    KEY idx_workflow_order (company_id, step_order),
    CONSTRAINT fk_wfstep_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE performance_cycles (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT       NOT NULL,
    name                    VARCHAR(150) NOT NULL,
    description             VARCHAR(500) NULL,
    scope_type              ENUM('MONTHLY','QUARTERLY','ANNUAL') NOT NULL DEFAULT 'QUARTERLY',
    period_year             SMALLINT     NOT NULL,
    period_index            TINYINT      NOT NULL,
    period_start            DATE         NOT NULL,
    period_end              DATE         NOT NULL,
    self_assessment_opens   DATE         NULL,
    self_assessment_closes  DATE         NULL,
    review_opens            DATE         NULL,
    review_closes           DATE         NULL,
    status                  ENUM('DRAFT','OPEN','IN_REVIEW','CLOSED') NOT NULL DEFAULT 'DRAFT',
    opened_at               DATETIME     NULL,
    closed_at               DATETIME     NULL,
    closed_by               BIGINT       NULL,
    is_active               TINYINT(1)   NOT NULL DEFAULT 1,
    created_at              DATETIME     NOT NULL,
    created_by              BIGINT       NULL,
    updated_at              DATETIME     NULL,
    updated_by              BIGINT       NULL,
    UNIQUE KEY uk_cycle_period (company_id, scope_type, period_year, period_index),
    KEY idx_cycle_status (company_id, status),
    CONSTRAINT fk_cycle_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One per employee per cycle: the unit that moves through the workflow.
CREATE TABLE appraisal_records (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT       NOT NULL,
    cycle_id                BIGINT       NOT NULL,
    employee_id             BIGINT       NOT NULL,
    manager_id              BIGINT       NULL,
    current_step            ENUM('SELF_ASSESSMENT','MANAGER_REVIEW','DEPARTMENT_HEAD_REVIEW',
                                 'HR_REVIEW','COMPLETED') NOT NULL DEFAULT 'SELF_ASSESSMENT',
    status                  ENUM('NOT_STARTED','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'NOT_STARTED',
    -- Copied from the performance snapshot when the cycle completes, so the
    -- appraisal keeps the figure it concluded on.
    computed_score          DECIMAL(5,2) NULL,
    final_score             DECIMAL(5,2) NULL,
    final_rating_band_id    BIGINT       NULL,
    completed_at            DATETIME     NULL,
    created_at              DATETIME     NOT NULL,
    created_by              BIGINT       NULL,
    updated_at              DATETIME     NULL,
    updated_by              BIGINT       NULL,
    UNIQUE KEY uk_appraisal_cycle_employee (cycle_id, employee_id),
    KEY idx_appraisal_employee (employee_id),
    KEY idx_appraisal_step (cycle_id, current_step),
    KEY idx_appraisal_manager (manager_id, current_step),
    CONSTRAINT fk_appraisal_company  FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_appraisal_cycle    FOREIGN KEY (cycle_id) REFERENCES performance_cycles (id),
    CONSTRAINT fk_appraisal_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_appraisal_manager  FOREIGN KEY (manager_id) REFERENCES employees (id),
    CONSTRAINT fk_appraisal_band     FOREIGN KEY (final_rating_band_id)
        REFERENCES performance_rating_bands (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seven long free-text answers on one row: as utf8mb4 VARCHAR(4000) they would
-- total 112 KB and exceed InnoDB's 64 KB row limit, so they are stored off-row.
CREATE TABLE self_assessments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    appraisal_id        BIGINT       NOT NULL,
    employee_id         BIGINT       NOT NULL,
    achievements        TEXT        NULL,
    challenges          TEXT        NULL,
    skills_developed    TEXT        NULL,
    improvement_areas   TEXT        NULL,
    training_needed     TEXT        NULL,
    future_goals        TEXT        NULL,
    comments            TEXT        NULL,
    self_rating         DECIMAL(3,1) NULL,
    status              ENUM('DRAFT','SUBMITTED') NOT NULL DEFAULT 'DRAFT',
    submitted_at        DATETIME     NULL,
    created_at          DATETIME     NOT NULL,
    created_by          BIGINT       NULL,
    updated_at          DATETIME     NULL,
    updated_by          BIGINT       NULL,
    UNIQUE KEY uk_self_assessment (appraisal_id),
    CONSTRAINT fk_selfassess_company   FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_selfassess_appraisal FOREIGN KEY (appraisal_id) REFERENCES appraisal_records (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_selfassess_employee  FOREIGN KEY (employee_id) REFERENCES employees (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One row per reviewer role, so a manager review and a head review are two
-- rows on the same appraisal rather than one row overwritten twice.
CREATE TABLE manager_reviews (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT       NOT NULL,
    appraisal_id            BIGINT       NOT NULL,
    employee_id             BIGINT       NOT NULL,
    reviewer_employee_id    BIGINT       NOT NULL,
    reviewer_name           VARCHAR(150) NULL,
    reviewer_role           ENUM('MANAGER','DEPARTMENT_HEAD','HR') NOT NULL DEFAULT 'MANAGER',
    -- The competency ratings, 1 to 5.
    quality_rating          DECIMAL(3,1) NULL,
    productivity_rating     DECIMAL(3,1) NULL,
    timeliness_rating       DECIMAL(3,1) NULL,
    teamwork_rating         DECIMAL(3,1) NULL,
    communication_rating    DECIMAL(3,1) NULL,
    initiative_rating       DECIMAL(3,1) NULL,
    problem_solving_rating  DECIMAL(3,1) NULL,
    discipline_rating       DECIMAL(3,1) NULL,
    kra_rating              DECIMAL(3,1) NULL,
    kpi_rating              DECIMAL(3,1) NULL,
    overall_rating          DECIMAL(3,1) NULL,
    strengths               VARCHAR(4000) NULL,
    improvements            VARCHAR(4000) NULL,
    comments                VARCHAR(4000) NULL,
    status                  ENUM('DRAFT','SUBMITTED') NOT NULL DEFAULT 'DRAFT',
    submitted_at            DATETIME     NULL,
    created_at              DATETIME     NOT NULL,
    created_by              BIGINT       NULL,
    updated_at              DATETIME     NULL,
    updated_by              BIGINT       NULL,
    UNIQUE KEY uk_review_appraisal_role (appraisal_id, reviewer_role),
    KEY idx_review_reviewer (reviewer_employee_id, status),
    KEY idx_review_employee (employee_id),
    CONSTRAINT fk_review_company   FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_review_appraisal FOREIGN KEY (appraisal_id) REFERENCES appraisal_records (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_review_employee  FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_review_reviewer  FOREIGN KEY (reviewer_employee_id) REFERENCES employees (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Append-only trail of every workflow move.
CREATE TABLE review_step_actions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    appraisal_id        BIGINT       NOT NULL,
    step_type           VARCHAR(40)  NOT NULL,
    action              ENUM('SUBMITTED','APPROVED','RETURNED','SKIPPED','COMPLETED') NOT NULL,
    from_step           VARCHAR(40)  NULL,
    to_step             VARCHAR(40)  NULL,
    actor_employee_id   BIGINT       NULL,
    actor_name          VARCHAR(150) NULL,
    comments            VARCHAR(1000) NULL,
    created_at          DATETIME     NOT NULL,
    KEY idx_stepaction_appraisal (appraisal_id, created_at),
    CONSTRAINT fk_stepaction_company   FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_stepaction_appraisal FOREIGN KEY (appraisal_id) REFERENCES appraisal_records (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
