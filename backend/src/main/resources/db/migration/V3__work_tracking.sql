-- =====================================================================
-- Phase 2 : Work tracking
-- Projects, task catalogue, tasks, and the daily timesheet.
--
-- Model note: submission and approval happen per DAY, not per entry, so a
-- work_log_days header owns the status and the cached totals, and entries
-- hang off it. The entry keeps its own `status` for the progress of that
-- piece of work, which is a different question from "has the day been filed".
-- =====================================================================

CREATE TABLE projects (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    code                VARCHAR(30)  NOT NULL,
    name                VARCHAR(150) NOT NULL,
    description         VARCHAR(500) NULL,
    department_id       BIGINT       NULL,
    project_manager_id  BIGINT       NULL,
    client_name         VARCHAR(150) NULL,
    start_date          DATE         NULL,
    end_date            DATE         NULL,
    status              ENUM('PLANNED','ACTIVE','ON_HOLD','COMPLETED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    billable            TINYINT(1)   NOT NULL DEFAULT 0,
    is_active           TINYINT(1)   NOT NULL DEFAULT 1,
    created_at          DATETIME     NOT NULL,
    created_by          BIGINT       NULL,
    updated_at          DATETIME     NULL,
    updated_by          BIGINT       NULL,
    UNIQUE KEY uk_project_code_company (company_id, code),
    KEY idx_project_department (department_id),
    KEY idx_project_status (status),
    CONSTRAINT fk_project_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_project_dept    FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_project_manager FOREIGN KEY (project_manager_id) REFERENCES employees (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Activity catalogue. `category_type` drives the day totals; `is_productive`
-- drives the productivity score in Phase 4. Both are data, not code.
CREATE TABLE task_categories (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    code                VARCHAR(40)  NOT NULL,
    name                VARCHAR(120) NOT NULL,
    description         VARCHAR(400) NULL,
    category_type       ENUM('WORK','MEETING','BREAK','ADMIN','TRAINING','SUPPORT','OTHER')
                        NOT NULL DEFAULT 'WORK',
    is_productive       TINYINT(1)   NOT NULL DEFAULT 1,
    department_id       BIGINT       NULL,
    colour_hex          VARCHAR(7)   NULL,
    display_order       INT          NOT NULL DEFAULT 0,
    is_active           TINYINT(1)   NOT NULL DEFAULT 1,
    created_at          DATETIME     NOT NULL,
    created_by          BIGINT       NULL,
    updated_at          DATETIME     NULL,
    updated_by          BIGINT       NULL,
    UNIQUE KEY uk_category_code_company (company_id, code),
    KEY idx_category_department (department_id),
    CONSTRAINT fk_category_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_category_dept    FOREIGN KEY (department_id) REFERENCES departments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Department-specific task catalogue: what "a task" usually looks like here.
CREATE TABLE task_templates (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT       NOT NULL,
    department_id           BIGINT       NULL,
    task_category_id        BIGINT       NOT NULL,
    name                    VARCHAR(150) NOT NULL,
    description             VARCHAR(500) NULL,
    default_estimated_minutes INT        NULL,
    default_priority        ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'MEDIUM',
    recurrence              ENUM('NONE','DAILY','WEEKLY','MONTHLY') NOT NULL DEFAULT 'NONE',
    display_order           INT          NOT NULL DEFAULT 0,
    is_active               TINYINT(1)   NOT NULL DEFAULT 1,
    created_at              DATETIME     NOT NULL,
    created_by              BIGINT       NULL,
    updated_at              DATETIME     NULL,
    updated_by              BIGINT       NULL,
    KEY idx_template_department (department_id),
    KEY idx_template_category (task_category_id),
    CONSTRAINT fk_template_company  FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_template_dept     FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_template_category FOREIGN KEY (task_category_id) REFERENCES task_categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tasks (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    assignee_id         BIGINT       NOT NULL,
    assigned_by_id      BIGINT       NULL,
    project_id          BIGINT       NULL,
    task_category_id    BIGINT       NOT NULL,
    task_template_id    BIGINT       NULL,
    title               VARCHAR(200) NOT NULL,
    description         VARCHAR(4000) NULL,
    priority            ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'MEDIUM',
    status              ENUM('NOT_STARTED','IN_PROGRESS','COMPLETED','ON_HOLD','BLOCKED','CANCELLED')
                        NOT NULL DEFAULT 'NOT_STARTED',
    completion_pct      INT          NOT NULL DEFAULT 0,
    estimated_minutes   INT          NULL,
    actual_minutes      INT          NOT NULL DEFAULT 0,
    assigned_date       DATE         NOT NULL,
    due_date            DATE         NULL,
    completed_at        DATETIME     NULL,
    remarks             VARCHAR(500) NULL,
    is_active           TINYINT(1)   NOT NULL DEFAULT 1,
    created_at          DATETIME     NOT NULL,
    created_by          BIGINT       NULL,
    updated_at          DATETIME     NULL,
    updated_by          BIGINT       NULL,
    KEY idx_task_assignee_status (assignee_id, status),
    KEY idx_task_due (due_date),
    KEY idx_task_project (project_id),
    KEY idx_task_assigned_date (assigned_date),
    CONSTRAINT fk_task_company   FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_task_assignee  FOREIGN KEY (assignee_id) REFERENCES employees (id),
    CONSTRAINT fk_task_assigner  FOREIGN KEY (assigned_by_id) REFERENCES employees (id),
    CONSTRAINT fk_task_project   FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_task_category  FOREIGN KEY (task_category_id) REFERENCES task_categories (id),
    CONSTRAINT fk_task_template  FOREIGN KEY (task_template_id) REFERENCES task_templates (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One row per employee per date: the unit that is submitted, reviewed and,
-- in Phase 4, scored. Totals are cached on write so dashboards and the
-- scoring engine never re-aggregate the entry table.
CREATE TABLE work_log_days (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT   NOT NULL,
    employee_id             BIGINT   NOT NULL,
    work_date               DATE     NOT NULL,
    status                  ENUM('DRAFT','SUBMITTED','UNDER_REVIEW','APPROVED','REJECTED',
                                 'MODIFICATION_REQUESTED') NOT NULL DEFAULT 'DRAFT',
    office_minutes          INT      NOT NULL DEFAULT 0,
    logged_minutes          INT      NOT NULL DEFAULT 0,
    productive_minutes      INT      NOT NULL DEFAULT 0,
    meeting_minutes         INT      NOT NULL DEFAULT 0,
    break_minutes           INT      NOT NULL DEFAULT 0,
    unallocated_minutes     INT      NOT NULL DEFAULT 0,
    overtime_minutes        INT      NOT NULL DEFAULT 0,
    entry_count             INT      NOT NULL DEFAULT 0,
    submitted_at            DATETIME NULL,
    reviewed_by             BIGINT   NULL,
    reviewed_at             DATETIME NULL,
    review_comments         VARCHAR(500) NULL,
    created_at              DATETIME NOT NULL,
    created_by              BIGINT   NULL,
    updated_at              DATETIME NULL,
    updated_by              BIGINT   NULL,
    UNIQUE KEY uk_wld_employee_date (employee_id, work_date),
    KEY idx_wld_date_status (work_date, status),
    KEY idx_wld_status_reviewer (status, reviewed_by),
    CONSTRAINT fk_wld_company  FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_wld_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_wld_reviewer FOREIGN KEY (reviewed_by) REFERENCES employees (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE daily_work_logs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    work_log_day_id     BIGINT       NOT NULL,
    employee_id         BIGINT       NOT NULL,
    work_date           DATE         NOT NULL,
    start_time          TIME         NOT NULL,
    end_time            TIME         NOT NULL,
    duration_minutes    INT          NOT NULL,
    project_id          BIGINT       NULL,
    task_id             BIGINT       NULL,
    task_category_id    BIGINT       NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         VARCHAR(4000) NULL,
    priority            ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL DEFAULT 'MEDIUM',
    status              ENUM('NOT_STARTED','IN_PROGRESS','COMPLETED','ON_HOLD','BLOCKED','CANCELLED')
                        NOT NULL DEFAULT 'IN_PROGRESS',
    completion_pct      INT          NOT NULL DEFAULT 0,
    is_productive       TINYINT(1)   NOT NULL DEFAULT 1,
    is_billable         TINYINT(1)   NOT NULL DEFAULT 0,
    remarks             VARCHAR(500) NULL,
    created_at          DATETIME     NOT NULL,
    created_by          BIGINT       NULL,
    updated_at          DATETIME     NULL,
    updated_by          BIGINT       NULL,
    UNIQUE KEY uk_dwl_employee_slot (employee_id, work_date, start_time),
    KEY idx_dwl_emp_date  (employee_id, work_date, start_time),
    KEY idx_dwl_day       (work_log_day_id),
    KEY idx_dwl_task      (task_id),
    KEY idx_dwl_category  (task_category_id),
    CONSTRAINT fk_dwl_company  FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_dwl_day      FOREIGN KEY (work_log_day_id) REFERENCES work_log_days (id) ON DELETE CASCADE,
    CONSTRAINT fk_dwl_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_dwl_project  FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_dwl_task     FOREIGN KEY (task_id) REFERENCES tasks (id),
    CONSTRAINT fk_dwl_category FOREIGN KEY (task_category_id) REFERENCES task_categories (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Append-only history of every state change on a day, so "who returned this
-- and why" is always answerable.
CREATE TABLE work_log_approvals (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    work_log_day_id     BIGINT       NOT NULL,
    action              ENUM('SUBMITTED','APPROVED','REJECTED','MODIFICATION_REQUESTED','REOPENED')
                        NOT NULL,
    from_status         VARCHAR(30)  NOT NULL,
    to_status           VARCHAR(30)  NOT NULL,
    actor_employee_id   BIGINT       NULL,
    actor_name          VARCHAR(150) NULL,
    comments            VARCHAR(500) NULL,
    created_at          DATETIME     NOT NULL,
    KEY idx_wla_day (work_log_day_id, created_at),
    CONSTRAINT fk_wla_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_wla_day     FOREIGN KEY (work_log_day_id) REFERENCES work_log_days (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
