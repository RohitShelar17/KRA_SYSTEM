-- ---------------------------------------------------------------------
-- Partitioning the audit trail — an operational change, run deliberately.
--
-- WHY THIS IS NOT A FLYWAY MIGRATION
--
-- MySQL refuses foreign keys on a partitioned table, in either direction, and
-- it requires every unique key — the primary key included — to contain the
-- partition column. So partitioning costs either referential integrity or a
-- changed primary key, on a table that is fine until it is large.
--
-- audit_logs is the one growth table that can pay that price: it carries no
-- foreign keys by design (an audit row must survive the deletion of whatever
-- it describes), and nothing references it.
--
-- WHEN TO RUN IT
--
--   SELECT COUNT(*) FROM audit_logs;
--
-- Around ten million rows, or when the table file passes a few gigabytes,
-- dropping a year becomes worth more than the simplicity of one flat table.
-- Below that, the (created_at) index already answers every query the
-- application makes.
--
-- WHAT IT DOES
--
-- Widens the primary key to (id, created_at) — the auto-increment still works,
-- id is still unique in practice — and partitions by year. Dropping a year is
-- then one instant DDL statement instead of a delete that takes a lock and
-- leaves the file the same size.
--
-- Take a backup first, and run it in a maintenance window: the ALTER rebuilds
-- the table.
-- ---------------------------------------------------------------------

ALTER TABLE audit_logs
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (id, created_at);

ALTER TABLE audit_logs
    PARTITION BY RANGE (YEAR(created_at)) (
        PARTITION p2026 VALUES LESS THAN (2027),
        PARTITION p2027 VALUES LESS THAN (2028),
        PARTITION p2028 VALUES LESS THAN (2029),
        PARTITION p_future VALUES LESS THAN MAXVALUE
    );

-- Each January, before the year starts landing in p_future:
--
--   ALTER TABLE audit_logs REORGANIZE PARTITION p_future INTO (
--       PARTITION p2029 VALUES LESS THAN (2030),
--       PARTITION p_future VALUES LESS THAN MAXVALUE
--   );
--
-- And once a year is past the retention window and archived elsewhere:
--
--   ALTER TABLE audit_logs DROP PARTITION p2026;
--
-- Archive before dropping. The application has no delete path for audit rows,
-- and this script is the only reason one exists at all.
