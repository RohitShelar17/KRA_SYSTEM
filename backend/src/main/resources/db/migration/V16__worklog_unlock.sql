-- ---------------------------------------------------------------------
-- Per-day back-dating exceptions.
--
-- The validator refuses a work-log entry older than the company's back-dating
-- window, and both the error and the settings screen told people to ask their
-- manager — while no such thing existed. This is that thing: one row grants one
-- employee the right to file one date, and nothing else.
--
-- Narrow on purpose. Widening backdate_window_days is the alternative, and it
-- reopens every past day for everybody at once; a grant here reopens one day
-- for one person, says who allowed it and why, and stops on its own.
--
-- The grant is time-boxed rather than single-use: a day takes several entries,
-- so a one-shot token would be spent on the first one. It expires so that a
-- forgotten grant is not a permanent hole in the rule, and it can be revoked
-- before then.
-- ---------------------------------------------------------------------

CREATE TABLE work_log_unlocks (
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT       NOT NULL,
    employee_id       BIGINT       NOT NULL,
    work_date         DATE         NOT NULL,
    reason            VARCHAR(500) NOT NULL,
    granted_by_id     BIGINT       NULL,
    granted_by_name   VARCHAR(150) NULL,
    expires_at        DATETIME     NOT NULL,
    revoked_at        DATETIME     NULL,
    revoked_by_id     BIGINT       NULL,
    created_at        DATETIME     NOT NULL,
    created_by        BIGINT       NULL,
    updated_at        DATETIME     NULL,
    updated_by        BIGINT       NULL,
    -- The question asked on every back-dated entry: is this person allowed to
    -- file this date right now.
    KEY idx_unlock_lookup (employee_id, work_date, revoked_at, expires_at),
    KEY idx_unlock_company (company_id, work_date),
    CONSTRAINT fk_unlock_company  FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_unlock_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Granting one is worth telling the employee about: it is time-boxed, and a
-- grant nobody hears about expires unused. In-app is enough — the person can
-- sign in by definition, since they were trying to file when they were stopped.
INSERT INTO notification_templates
    (company_id, event_key, name, description, title_template, body_template, link_template,
     severity, in_app, email, is_active, created_at)
VALUES
(1, 'WORKLOG_UNLOCKED', 'Back-dated day unlocked',
 'Sent to an employee when a manager reopens a date outside the back-dating window',
 '{workDate} is open for you until {expiresAt}',
 '{grantedBy} reopened {workDate} so you can file it. Reason: {reason}. The date closes again on {expiresAt}.',
 '/worklogs?date={workDate}', 'INFO', 1, 0, 1, NOW());
