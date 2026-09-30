-- ---------------------------------------------------------------------
-- The unlock notification's link carried the human-readable date.
--
-- V16 used {workDate} for both the sentence and the link, so the message read
-- well ("Wednesday, 9 September 2026 is open for you") while the link came out
-- as /worklogs?date=Wednesday, 9 September 2026 — which opens nothing.
--
-- They are two different needs from one value, so there are now two
-- placeholders: {workDate} reads, {workDateIso} resolves. V16 is left as it
-- was rather than edited, because it has already run.
-- ---------------------------------------------------------------------

UPDATE notification_templates
SET link_template = '/worklogs?date={workDateIso}'
WHERE event_key = 'WORKLOG_UNLOCKED';
