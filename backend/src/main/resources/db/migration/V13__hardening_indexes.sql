-- ---------------------------------------------------------------------
-- Phase 7 — indexes for the access paths Phases 5 and 6 added.
--
-- Only three: an index that is never the cheapest plan still costs a write
-- on every insert, and the earlier phases already indexed their own reads.
-- Each one below answers a query that currently scans.
-- ---------------------------------------------------------------------

-- Nightly retention: DELETE ... WHERE is_read = TRUE AND created_at < ?
-- The feed index leads with the recipient, so it cannot serve this.
CREATE INDEX idx_notification_retention ON notifications (is_read, created_at);

-- The work-log report reads a date window across everyone in a company;
-- the existing index leads with employee_id and is no use without one.
CREATE INDEX idx_dwl_company_date ON daily_work_logs (company_id, work_date);

-- The reminder job walks every appraisal still in flight for a company.
CREATE INDEX idx_appraisal_company_status ON appraisal_records (company_id, status);
