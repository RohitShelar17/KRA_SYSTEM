-- =====================================================================
-- Phase 1 : Foundation baseline
-- Identity, organisation, people and the cross-cutting audit trail.
-- Later phases add their own tables in their own migrations.
-- Audit columns (created_by / updated_by) hold user ids but carry no FK,
-- so seed data and system jobs can write rows without ordering problems.
-- =====================================================================

CREATE TABLE companies (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    name                VARCHAR(150) NOT NULL,
    code                VARCHAR(30)  NOT NULL,
    legal_name          VARCHAR(200) NULL,
    email               VARCHAR(150) NULL,
    phone               VARCHAR(30)  NULL,
    address             VARCHAR(400) NULL,
    timezone            VARCHAR(60)  NOT NULL DEFAULT 'Asia/Kolkata',
    currency_code       VARCHAR(3)   NOT NULL DEFAULT 'INR',
    is_active           TINYINT(1)   NOT NULL DEFAULT 1,
    created_at          DATETIME     NOT NULL,
    created_by          BIGINT       NULL,
    updated_at          DATETIME     NULL,
    updated_by          BIGINT       NULL,
    UNIQUE KEY uk_company_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE company_settings (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT       NOT NULL,
    work_start_time         TIME         NOT NULL DEFAULT '09:00:00',
    work_end_time           TIME         NOT NULL DEFAULT '18:00:00',
    break_minutes           INT          NOT NULL DEFAULT 60,
    working_days            VARCHAR(30)  NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI',
    worklog_approval_enabled TINYINT(1)  NOT NULL DEFAULT 1,
    overtime_allowed        TINYINT(1)   NOT NULL DEFAULT 1,
    max_overtime_minutes    INT          NOT NULL DEFAULT 180,
    backdate_window_days    INT          NOT NULL DEFAULT 3,
    worklog_cutoff_time     TIME         NOT NULL DEFAULT '20:00:00',
    min_submission_rate_pct DECIMAL(5,2) NOT NULL DEFAULT 80.00,
    created_at              DATETIME     NOT NULL,
    created_by              BIGINT       NULL,
    updated_at              DATETIME     NULL,
    updated_by              BIGINT       NULL,
    UNIQUE KEY uk_settings_company (company_id),
    CONSTRAINT fk_settings_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Identity and access
-- ---------------------------------------------------------------------

CREATE TABLE permissions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    code            VARCHAR(60)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    module          VARCHAR(40)  NOT NULL,
    description     VARCHAR(255) NULL,
    created_at      DATETIME     NOT NULL,
    UNIQUE KEY uk_permission_code (code),
    KEY idx_permission_module (module)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE roles (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT       NOT NULL,
    code            VARCHAR(40)  NOT NULL,
    name            VARCHAR(80)  NOT NULL,
    description     VARCHAR(255) NULL,
    data_scope      ENUM('SELF','REPORTS','DEPARTMENT','ORGANISATION') NOT NULL DEFAULT 'SELF',
    is_system_role  TINYINT(1)   NOT NULL DEFAULT 0,
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL,
    created_by      BIGINT       NULL,
    updated_at      DATETIME     NULL,
    updated_by      BIGINT       NULL,
    UNIQUE KEY uk_role_code_company (company_id, code),
    CONSTRAINT fk_role_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE role_permissions (
    role_id         BIGINT NOT NULL,
    permission_id   BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role       FOREIGN KEY (role_id)       REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT       NOT NULL,
    username                VARCHAR(100) NOT NULL,
    email                   VARCHAR(150) NOT NULL,
    password_hash           VARCHAR(100) NOT NULL,
    status                  ENUM('ACTIVE','INACTIVE','LOCKED','PENDING_ACTIVATION') NOT NULL DEFAULT 'ACTIVE',
    must_change_password    TINYINT(1)   NOT NULL DEFAULT 0,
    failed_login_attempts   INT          NOT NULL DEFAULT 0,
    locked_until            DATETIME     NULL,
    last_login_at           DATETIME     NULL,
    password_changed_at     DATETIME     NULL,
    reset_token             VARCHAR(120) NULL,
    reset_token_expires_at  DATETIME     NULL,
    is_active               TINYINT(1)   NOT NULL DEFAULT 1,
    created_at              DATETIME     NOT NULL,
    created_by              BIGINT       NULL,
    updated_at              DATETIME     NULL,
    updated_by              BIGINT       NULL,
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_email (email),
    KEY idx_user_reset_token (reset_token),
    CONSTRAINT fk_user_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_roles (
    user_id     BIGINT NOT NULL,
    role_id     BIGINT NOT NULL,
    -- Defaults here rather than in the entity: the join is mapped as a plain
    -- @ManyToMany, so Hibernate writes only the two key columns.
    assigned_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by BIGINT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE refresh_tokens (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    token_hash      VARCHAR(120) NOT NULL,
    family_id       VARCHAR(60)  NOT NULL,
    issued_at       DATETIME     NOT NULL,
    expires_at      DATETIME     NOT NULL,
    revoked_at      DATETIME     NULL,
    revoked_reason  VARCHAR(120) NULL,
    ip_address      VARCHAR(45)  NULL,
    user_agent      VARCHAR(255) NULL,
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_user (user_id, expires_at),
    KEY idx_refresh_family (family_id),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Organisation structure
-- ---------------------------------------------------------------------

CREATE TABLE departments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    code                VARCHAR(30)  NOT NULL,
    name                VARCHAR(120) NOT NULL,
    description         VARCHAR(400) NULL,
    parent_department_id BIGINT      NULL,
    head_employee_id    BIGINT       NULL,
    is_active           TINYINT(1)   NOT NULL DEFAULT 1,
    created_at          DATETIME     NOT NULL,
    created_by          BIGINT       NULL,
    updated_at          DATETIME     NULL,
    updated_by          BIGINT       NULL,
    UNIQUE KEY uk_dept_code_company (company_id, code),
    KEY idx_dept_parent (parent_department_id),
    CONSTRAINT fk_dept_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_dept_parent  FOREIGN KEY (parent_department_id) REFERENCES departments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE designations (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT       NOT NULL,
    code            VARCHAR(30)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    grade_level     VARCHAR(20)  NULL,
    department_id   BIGINT       NULL,
    description     VARCHAR(400) NULL,
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL,
    created_by      BIGINT       NULL,
    updated_at      DATETIME     NULL,
    updated_by      BIGINT       NULL,
    UNIQUE KEY uk_desig_code_company (company_id, code),
    CONSTRAINT fk_desig_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_desig_dept    FOREIGN KEY (department_id) REFERENCES departments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE teams (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    department_id       BIGINT       NOT NULL,
    code                VARCHAR(30)  NOT NULL,
    name                VARCHAR(120) NOT NULL,
    description         VARCHAR(400) NULL,
    team_lead_employee_id BIGINT     NULL,
    is_active           TINYINT(1)   NOT NULL DEFAULT 1,
    created_at          DATETIME     NOT NULL,
    created_by          BIGINT       NULL,
    updated_at          DATETIME     NULL,
    updated_by          BIGINT       NULL,
    UNIQUE KEY uk_team_code_company (company_id, code),
    KEY idx_team_dept (department_id),
    CONSTRAINT fk_team_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_team_dept    FOREIGN KEY (department_id) REFERENCES departments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE holidays (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT       NOT NULL,
    holiday_date    DATE         NOT NULL,
    name            VARCHAR(120) NOT NULL,
    holiday_type    ENUM('PUBLIC','RESTRICTED','COMPANY','WEEK_OFF') NOT NULL DEFAULT 'PUBLIC',
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL,
    created_by      BIGINT       NULL,
    updated_at      DATETIME     NULL,
    updated_by      BIGINT       NULL,
    UNIQUE KEY uk_holiday_date_company (company_id, holiday_date),
    CONSTRAINT fk_holiday_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- People
-- ---------------------------------------------------------------------

CREATE TABLE employees (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT       NOT NULL,
    user_id             BIGINT       NULL,
    employee_code       VARCHAR(30)  NOT NULL,
    first_name          VARCHAR(80)  NOT NULL,
    last_name           VARCHAR(80)  NOT NULL,
    email               VARCHAR(150) NOT NULL,
    phone               VARCHAR(30)  NULL,
    department_id       BIGINT       NOT NULL,
    team_id             BIGINT       NULL,
    designation_id      BIGINT       NOT NULL,
    reporting_manager_id BIGINT      NULL,
    joining_date        DATE         NOT NULL,
    exit_date           DATE         NULL,
    employment_type     ENUM('FULL_TIME','PART_TIME','CONTRACT','INTERN','CONSULTANT') NOT NULL DEFAULT 'FULL_TIME',
    work_location       VARCHAR(120) NULL,
    work_mode           ENUM('OFFICE','REMOTE','HYBRID') NOT NULL DEFAULT 'OFFICE',
    shift_start_time    TIME         NULL,
    shift_end_time      TIME         NULL,
    status              ENUM('ACTIVE','ON_NOTICE','INACTIVE','TERMINATED','ON_LEAVE') NOT NULL DEFAULT 'ACTIVE',
    profile_image_url   VARCHAR(400) NULL,
    is_active           TINYINT(1)   NOT NULL DEFAULT 1,
    created_at          DATETIME     NOT NULL,
    created_by          BIGINT       NULL,
    updated_at          DATETIME     NULL,
    updated_by          BIGINT       NULL,
    UNIQUE KEY uk_emp_code_company (company_id, employee_code),
    UNIQUE KEY uk_emp_email (email),
    UNIQUE KEY uk_emp_user (user_id),
    KEY idx_emp_department (department_id),
    KEY idx_emp_manager (reporting_manager_id),
    KEY idx_emp_status (status),
    KEY idx_emp_name (last_name, first_name),
    CONSTRAINT fk_emp_company  FOREIGN KEY (company_id)     REFERENCES companies (id),
    CONSTRAINT fk_emp_user     FOREIGN KEY (user_id)        REFERENCES users (id),
    CONSTRAINT fk_emp_dept     FOREIGN KEY (department_id)  REFERENCES departments (id),
    CONSTRAINT fk_emp_team     FOREIGN KEY (team_id)        REFERENCES teams (id),
    CONSTRAINT fk_emp_desig    FOREIGN KEY (designation_id) REFERENCES designations (id),
    CONSTRAINT fk_emp_manager  FOREIGN KEY (reporting_manager_id) REFERENCES employees (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Effective-dated reporting relationships: a past appraisal must still resolve
-- the manager who actually held the relationship at that time.
CREATE TABLE reporting_hierarchy (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT   NOT NULL,
    employee_id     BIGINT   NOT NULL,
    manager_id      BIGINT   NOT NULL,
    relation_type   ENUM('PRIMARY','DOTTED_LINE') NOT NULL DEFAULT 'PRIMARY',
    valid_from      DATE     NOT NULL,
    valid_to        DATE     NULL,
    created_at      DATETIME NOT NULL,
    created_by      BIGINT   NULL,
    updated_at      DATETIME NULL,
    updated_by      BIGINT   NULL,
    KEY idx_rh_employee (employee_id, valid_from),
    KEY idx_rh_manager  (manager_id, valid_from),
    CONSTRAINT fk_rh_company  FOREIGN KEY (company_id)  REFERENCES companies (id),
    CONSTRAINT fk_rh_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_rh_manager  FOREIGN KEY (manager_id)  REFERENCES employees (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE departments
    ADD CONSTRAINT fk_dept_head FOREIGN KEY (head_employee_id) REFERENCES employees (id);
ALTER TABLE teams
    ADD CONSTRAINT fk_team_lead FOREIGN KEY (team_lead_employee_id) REFERENCES employees (id);

-- ---------------------------------------------------------------------
-- Audit trail (insert only)
-- ---------------------------------------------------------------------

CREATE TABLE audit_logs (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT       NULL,
    actor_user_id   BIGINT       NULL,
    actor_name      VARCHAR(150) NULL,
    action          VARCHAR(30)  NOT NULL,
    module          VARCHAR(40)  NOT NULL,
    entity_type     VARCHAR(60)  NULL,
    entity_id       BIGINT       NULL,
    old_value       JSON         NULL,
    new_value       JSON         NULL,
    changed_fields  VARCHAR(1000) NULL,
    reason          VARCHAR(500) NULL,
    ip_address      VARCHAR(45)  NULL,
    user_agent      VARCHAR(255) NULL,
    trace_id        VARCHAR(40)  NULL,
    created_at      DATETIME     NOT NULL,
    KEY idx_audit_entity (entity_type, entity_id),
    KEY idx_audit_actor  (actor_user_id, created_at),
    KEY idx_audit_created (created_at),
    KEY idx_audit_module (module, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
