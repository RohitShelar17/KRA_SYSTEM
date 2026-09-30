-- ---------------------------------------------------------------------
-- Phase 6 — Insight: notifications and report requests.
--
-- Notifications are stored per recipient, and delivery is recorded per
-- recipient per channel, so "nobody told me" is an answerable question.
-- Report requests record who ran what, with which filters, and how many
-- rows left the system — the export audit the blueprint asks for.
-- ---------------------------------------------------------------------

CREATE TABLE notification_templates (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT       NOT NULL,
    event_key       VARCHAR(60)  NOT NULL,
    name            VARCHAR(120) NOT NULL,
    description     VARCHAR(400) NULL,
    -- Placeholders are {name} tokens resolved from the event's payload.
    title_template  VARCHAR(200) NOT NULL,
    body_template   VARCHAR(1000) NOT NULL,
    link_template   VARCHAR(200) NULL,
    severity        ENUM('INFO','SUCCESS','WARNING','CRITICAL') NOT NULL DEFAULT 'INFO',
    in_app          TINYINT(1)   NOT NULL DEFAULT 1,
    email           TINYINT(1)   NOT NULL DEFAULT 0,
    is_active       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at      DATETIME     NOT NULL,
    created_by      BIGINT       NULL,
    updated_at      DATETIME     NULL,
    updated_by      BIGINT       NULL,
    UNIQUE KEY uk_template_event (company_id, event_key),
    CONSTRAINT fk_notiftemplate_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE notifications (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id            BIGINT       NOT NULL,
    recipient_user_id     BIGINT       NOT NULL,
    recipient_employee_id BIGINT       NULL,
    event_key             VARCHAR(60)  NOT NULL,
    title                 VARCHAR(200) NOT NULL,
    body                  VARCHAR(1000) NOT NULL,
    link                  VARCHAR(200) NULL,
    severity              ENUM('INFO','SUCCESS','WARNING','CRITICAL') NOT NULL DEFAULT 'INFO',
    entity_type           VARCHAR(60)  NULL,
    entity_id             BIGINT       NULL,
    is_read               TINYINT(1)   NOT NULL DEFAULT 0,
    read_at               DATETIME     NULL,
    created_at            DATETIME     NOT NULL,
    created_by            BIGINT       NULL,
    updated_at            DATETIME     NULL,
    updated_by            BIGINT       NULL,
    -- The feed query: one recipient's unread, newest first.
    KEY idx_notification_feed (recipient_user_id, is_read, created_at),
    KEY idx_notification_entity (entity_type, entity_id),
    CONSTRAINT fk_notification_company   FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One row per notification per channel. A channel that is not configured
-- records SKIPPED with the reason rather than silently dropping the message.
CREATE TABLE notification_deliveries (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id      BIGINT       NOT NULL,
    notification_id BIGINT       NOT NULL,
    channel         ENUM('IN_APP','EMAIL') NOT NULL,
    status          ENUM('PENDING','SENT','FAILED','SKIPPED') NOT NULL DEFAULT 'PENDING',
    attempts        INT          NOT NULL DEFAULT 0,
    detail          VARCHAR(500) NULL,
    sent_at         DATETIME     NULL,
    created_at      DATETIME     NOT NULL,
    created_by      BIGINT       NULL,
    updated_at      DATETIME     NULL,
    updated_by      BIGINT       NULL,
    UNIQUE KEY uk_delivery_channel (notification_id, channel),
    KEY idx_delivery_retry (status, attempts),
    CONSTRAINT fk_delivery_company      FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_delivery_notification FOREIGN KEY (notification_id) REFERENCES notifications (id)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE report_requests (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id        BIGINT       NOT NULL,
    report_key        VARCHAR(60)  NOT NULL,
    report_title      VARCHAR(150) NOT NULL,
    format            ENUM('CSV','XLSX','PDF') NOT NULL,
    -- The filter set, kept so an export can be reproduced or questioned.
    filters           JSON         NULL,
    status            ENUM('QUEUED','RUNNING','READY','FAILED') NOT NULL DEFAULT 'QUEUED',
    row_count         INT          NULL,
    file_name         VARCHAR(200) NULL,
    file_path         VARCHAR(500) NULL,
    byte_size         BIGINT       NULL,
    error_detail      VARCHAR(500) NULL,
    requested_by      BIGINT       NOT NULL,
    requested_by_name VARCHAR(150) NULL,
    completed_at      DATETIME     NULL,
    created_at        DATETIME     NOT NULL,
    created_by        BIGINT       NULL,
    updated_at        DATETIME     NULL,
    updated_by        BIGINT       NULL,
    KEY idx_report_requester (requested_by, created_at),
    KEY idx_report_key (company_id, report_key, created_at),
    CONSTRAINT fk_report_company FOREIGN KEY (company_id) REFERENCES companies (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
