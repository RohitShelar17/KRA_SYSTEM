-- =====================================================================
-- Phase 3 : KRA and KPI
--
-- Templates describe what a role is measured on. Assigning a template
-- COPIES it onto the employee: templates keep evolving, but a signed-off
-- assignment must not change under someone's feet, and a past appraisal has
-- to stay reconstructible.
--
-- Weightage totals (KRAs within a template, KPIs within a KRA) are enforced
-- in the service layer inside the same transaction as the write — a CHECK
-- constraint cannot span rows.
-- =====================================================================

CREATE TABLE kra_templates (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT       NOT NULL,
    code            VARCHAR(40)  NOT NULL,
    name            VARCHAR(150) NOT NULL,
    description     VARCHAR(500) NULL,
    designation_id  BIGINT       NULL,
    department_id   BIGINT       NULL,
    period_type     ENUM('MONTHLY','QUARTERLY','ANNUAL') NOT NULL DEFAULT 'QUARTERLY',
    is_published    TINYINT(1)   NOT NULL DEFAULT 0,
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL,
    created_by      BIGINT       NULL,
    updated_at      DATETIME     NULL,
    updated_by      BIGINT       NULL,
    UNIQUE KEY uk_kra_template_code (company_id, code),
    KEY idx_kra_template_designation (designation_id),
    KEY idx_kra_template_department (department_id),
    CONSTRAINT fk_kratpl_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_kratpl_desig   FOREIGN KEY (designation_id) REFERENCES designations (id),
    CONSTRAINT fk_kratpl_dept    FOREIGN KEY (department_id) REFERENCES departments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE kra_template_items (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT        NOT NULL,
    kra_template_id     BIGINT        NOT NULL,
    name                VARCHAR(150)  NOT NULL,
    description         VARCHAR(500)  NULL,
    weightage           DECIMAL(5,2)  NOT NULL,
    display_order       INT           NOT NULL DEFAULT 0,
    created_at          DATETIME      NOT NULL,
    created_by          BIGINT        NULL,
    updated_at          DATETIME      NULL,
    updated_by          BIGINT        NULL,
    KEY idx_kra_item_template (kra_template_id, display_order),
    CONSTRAINT fk_kraitem_company  FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_kraitem_template FOREIGN KEY (kra_template_id) REFERENCES kra_templates (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE kpi_template_items (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id              BIGINT        NOT NULL,
    kra_template_item_id    BIGINT        NOT NULL,
    name                    VARCHAR(150)  NOT NULL,
    description             VARCHAR(500)  NULL,
    measurement_type        ENUM('PERCENTAGE','NUMERIC','CURRENCY','HOURS','COUNT','BOOLEAN','RATING','CUSTOM')
                            NOT NULL DEFAULT 'PERCENTAGE',
    target_direction        ENUM('HIGHER_IS_BETTER','LOWER_IS_BETTER','BOOLEAN')
                            NOT NULL DEFAULT 'HIGHER_IS_BETTER',
    unit                    VARCHAR(30)   NULL,
    default_target_value    DECIMAL(15,2) NULL,
    weightage               DECIMAL(5,2)  NOT NULL,
    cap_percent             DECIMAL(5,2)  NOT NULL DEFAULT 120.00,
    actual_source           ENUM('MANUAL','WORK_LOG_PRODUCTIVE_HOURS','TASK_COMPLETION_RATE',
                                 'TASK_ON_TIME_RATE','INTEGRATION') NOT NULL DEFAULT 'MANUAL',
    display_order           INT           NOT NULL DEFAULT 0,
    created_at              DATETIME      NOT NULL,
    created_by              BIGINT        NULL,
    updated_at              DATETIME      NULL,
    updated_by              BIGINT        NULL,
    KEY idx_kpi_item_kra (kra_template_item_id, display_order),
    CONSTRAINT fk_kpiitem_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_kpiitem_kra     FOREIGN KEY (kra_template_item_id) REFERENCES kra_template_items (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- Assignment: a copy of the template, owned by the employee and the period
-- ---------------------------------------------------------------------

CREATE TABLE employee_kras (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT        NOT NULL,
    employee_id         BIGINT        NOT NULL,
    kra_template_id     BIGINT        NULL,
    name                VARCHAR(150)  NOT NULL,
    description         VARCHAR(500)  NULL,
    weightage           DECIMAL(5,2)  NOT NULL,
    period_type         ENUM('MONTHLY','QUARTERLY','ANNUAL') NOT NULL DEFAULT 'QUARTERLY',
    period_year         SMALLINT      NOT NULL,
    period_index        TINYINT       NOT NULL,
    period_start        DATE          NOT NULL,
    period_end          DATE          NOT NULL,
    status              ENUM('DRAFT','ACTIVE','CLOSED') NOT NULL DEFAULT 'ACTIVE',
    display_order       INT           NOT NULL DEFAULT 0,
    assigned_by         BIGINT        NULL,
    is_active           TINYINT(1)    NOT NULL DEFAULT 1,
    created_at          DATETIME      NOT NULL,
    created_by          BIGINT        NULL,
    updated_at          DATETIME      NULL,
    updated_by          BIGINT        NULL,
    KEY idx_emp_kra_employee (employee_id, period_year, period_index),
    KEY idx_emp_kra_period (period_start, period_end),
    CONSTRAINT fk_empkra_company  FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_empkra_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_empkra_template FOREIGN KEY (kra_template_id) REFERENCES kra_templates (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE employee_kpis (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT        NOT NULL,
    employee_kra_id     BIGINT        NOT NULL,
    employee_id         BIGINT        NOT NULL,
    name                VARCHAR(150)  NOT NULL,
    description         VARCHAR(500)  NULL,
    measurement_type    ENUM('PERCENTAGE','NUMERIC','CURRENCY','HOURS','COUNT','BOOLEAN','RATING','CUSTOM')
                        NOT NULL DEFAULT 'PERCENTAGE',
    target_direction    ENUM('HIGHER_IS_BETTER','LOWER_IS_BETTER','BOOLEAN')
                        NOT NULL DEFAULT 'HIGHER_IS_BETTER',
    unit                VARCHAR(30)   NULL,
    weightage           DECIMAL(5,2)  NOT NULL,
    cap_percent         DECIMAL(5,2)  NOT NULL DEFAULT 120.00,
    actual_source       ENUM('MANUAL','WORK_LOG_PRODUCTIVE_HOURS','TASK_COMPLETION_RATE',
                             'TASK_ON_TIME_RATE','INTEGRATION') NOT NULL DEFAULT 'MANUAL',
    display_order       INT           NOT NULL DEFAULT 0,
    is_active           TINYINT(1)    NOT NULL DEFAULT 1,
    created_at          DATETIME      NOT NULL,
    created_by          BIGINT        NULL,
    updated_at          DATETIME      NULL,
    updated_by          BIGINT        NULL,
    KEY idx_emp_kpi_kra (employee_kra_id, display_order),
    KEY idx_emp_kpi_employee (employee_id),
    CONSTRAINT fk_empkpi_company  FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_empkpi_kra      FOREIGN KEY (employee_kra_id) REFERENCES employee_kras (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_empkpi_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- A KPI can be targeted at a finer grain than its KRA period: a monthly
-- revenue target inside a quarterly KRA, for instance.
CREATE TABLE kpi_targets (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT        NOT NULL,
    employee_kpi_id     BIGINT        NOT NULL,
    period_type         ENUM('DAILY','WEEKLY','MONTHLY','QUARTERLY','ANNUAL') NOT NULL,
    period_start        DATE          NOT NULL,
    period_end          DATE          NOT NULL,
    target_value        DECIMAL(15,2) NOT NULL,
    stretch_value       DECIMAL(15,2) NULL,
    notes               VARCHAR(400)  NULL,
    created_at          DATETIME      NOT NULL,
    created_by          BIGINT        NULL,
    updated_at          DATETIME      NULL,
    updated_by          BIGINT        NULL,
    UNIQUE KEY uk_kpi_target_period (employee_kpi_id, period_start, period_end),
    KEY idx_kpi_target_range (employee_kpi_id, period_start),
    CONSTRAINT fk_kpitarget_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_kpitarget_kpi     FOREIGN KEY (employee_kpi_id) REFERENCES employee_kpis (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE kpi_actuals (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id          BIGINT        NOT NULL,
    employee_kpi_id     BIGINT        NOT NULL,
    period_start        DATE          NOT NULL,
    period_end          DATE          NOT NULL,
    actual_value        DECIMAL(15,2) NOT NULL,
    -- Where the number came from matters: a manager-entered figure and one
    -- derived from the timesheet are not the same kind of evidence.
    source              ENUM('MANUAL','WORK_LOG_PRODUCTIVE_HOURS','TASK_COMPLETION_RATE',
                             'TASK_ON_TIME_RATE','INTEGRATION') NOT NULL DEFAULT 'MANUAL',
    recorded_by         BIGINT        NULL,
    recorded_by_name    VARCHAR(150)  NULL,
    note                VARCHAR(400)  NULL,
    created_at          DATETIME      NOT NULL,
    created_by          BIGINT        NULL,
    updated_at          DATETIME      NULL,
    updated_by          BIGINT        NULL,
    UNIQUE KEY uk_kpi_actual_period (employee_kpi_id, period_start, period_end),
    KEY idx_kpi_actual_range (employee_kpi_id, period_start),
    CONSTRAINT fk_kpiactual_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_kpiactual_kpi     FOREIGN KEY (employee_kpi_id) REFERENCES employee_kpis (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Work-log entries can be attributed to a KRA, which is how a day's time
-- rolls up to a result area in Phase 4.
ALTER TABLE daily_work_logs
    ADD COLUMN employee_kra_id BIGINT NULL AFTER task_category_id,
    ADD KEY idx_dwl_kra (employee_kra_id),
    ADD CONSTRAINT fk_dwl_kra FOREIGN KEY (employee_kra_id) REFERENCES employee_kras (id);

ALTER TABLE tasks
    ADD COLUMN employee_kra_id BIGINT NULL AFTER task_template_id,
    ADD KEY idx_task_kra (employee_kra_id),
    ADD CONSTRAINT fk_task_kra FOREIGN KEY (employee_kra_id) REFERENCES employee_kras (id);
