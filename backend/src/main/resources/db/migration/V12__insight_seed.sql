-- ---------------------------------------------------------------------
-- Phase 6 seed — the notification catalogue.
--
-- Wording lives in rows, not in Java string literals: an administrator can
-- reword "your day was returned" without a release. Placeholders in {braces}
-- are filled from the event that raised the notification; an unknown
-- placeholder is left as written rather than printed as null.
-- ---------------------------------------------------------------------

INSERT INTO notification_templates
    (company_id, event_key, name, description, title_template, body_template, link_template,
     severity, in_app, email, is_active, created_at)
VALUES
(1, 'WORKLOG_SUBMITTED', 'Work log submitted',
 'Sent to the reviewing manager when somebody files their day',
 '{employeeName} submitted {workDate}',
 '{employeeName} filed {loggedHours} hours for {workDate} and it is waiting for your review.',
 '/team/reviews', 'INFO', 1, 0, 1, NOW()),

(1, 'WORKLOG_REVIEWED', 'Work log reviewed',
 'Sent to the employee when a manager decides on their day',
 'Your {workDate} log was {decision}',
 '{reviewerName} {decision} your work log for {workDate}.{comments}',
 '/worklogs', 'INFO', 1, 0, 1, NOW()),

(1, 'WORKLOG_MISSING', 'Work log not submitted',
 'Daily reminder to anybody who has not filed a working day',
 'Your {workDate} log is not submitted',
 '{workDate} is still open. File it so the day counts towards your score.',
 '/worklogs', 'WARNING', 1, 0, 1, NOW()),

(1, 'TASK_ASSIGNED', 'Task assigned',
 'Sent to the assignee when a task is created for or reassigned to them',
 '{assignerName} assigned you "{taskTitle}"',
 '"{taskTitle}" is due {dueDate} with an estimate of {estimatedHours} hours.',
 '/tasks', 'INFO', 1, 0, 1, NOW()),

(1, 'TASK_DUE_SOON', 'Task due tomorrow',
 'Reminder to the assignee the day before a task is due',
 '"{taskTitle}" is due {dueDate}',
 'This task is still {status} and is due {dueDate}.',
 '/tasks', 'INFO', 1, 0, 1, NOW()),

(1, 'TASK_OVERDUE', 'Task overdue',
 'Reminder to the assignee, and their manager, once a task passes its due date',
 '"{taskTitle}" is overdue',
 'This task was due {dueDate} and is still {status}.',
 '/tasks', 'WARNING', 1, 0, 1, NOW()),

(1, 'SCORE_PUBLISHED', 'Score published',
 'Sent to the employee when a period score is computed',
 'Your {periodLabel} score is {score}',
 'Your {scope} score for {periodLabel} is {score}{bandSuffix}. The full breakdown shows how it was reached.',
 '/performance', 'SUCCESS', 1, 0, 1, NOW()),

(1, 'PERFORMANCE_FINALIZED', 'Score finalised',
 'Sent to the employee when a snapshot is frozen',
 'Your {periodLabel} score is final',
 '{actorName} finalised your {periodLabel} score at {score}. It can no longer change.',
 '/performance', 'SUCCESS', 1, 0, 1, NOW()),

(1, 'PERFORMANCE_REOPENED', 'Score reopened',
 'Sent to the employee, their manager and HR when a finalised score is unfrozen',
 '{periodLabel} score reopened',
 '{actorName} reopened the {periodLabel} score for {employeeName}. Reason: {reason}',
 '/performance', 'WARNING', 1, 0, 1, NOW()),

(1, 'CYCLE_OPENED', 'Appraisal cycle open',
 'Sent to every participant when HR opens a cycle',
 '{cycleName} is open',
 'Your self assessment for {periodLabel} is open{windowSuffix}. Write it before the window closes.',
 '/self-assessment', 'INFO', 1, 0, 1, NOW()),

(1, 'APPRAISAL_STEP_READY', 'Appraisal waiting on you',
 'Sent to the reviewer who owns the step an appraisal has just reached',
 '{employeeName}''s appraisal is at {stepName}',
 '{employeeName}''s appraisal for {periodLabel} has reached {stepName} and is waiting on you.',
 '/team/appraisals', 'INFO', 1, 0, 1, NOW()),

(1, 'APPRAISAL_RETURNED', 'Appraisal sent back',
 'Sent to whoever owns the step an appraisal was returned to',
 'Your appraisal was sent back',
 '{actorName} returned the appraisal for {periodLabel} to {stepName}. Reason: {reason}',
 '/self-assessment', 'WARNING', 1, 0, 1, NOW()),

(1, 'APPRAISAL_COMPLETED', 'Appraisal complete',
 'Sent to the employee when the last workflow step signs off',
 'Your {periodLabel} appraisal is complete',
 '{actorName} signed off your appraisal for {periodLabel}. The reviewers'' feedback is on the record.',
 '/self-assessment', 'SUCCESS', 1, 0, 1, NOW()),

(1, 'REVIEW_OVERDUE', 'Review past its SLA',
 'Daily reminder to a reviewer sitting on an appraisal beyond the configured SLA',
 '{employeeName}''s appraisal has been waiting {daysWaiting} days',
 '{stepName} for {employeeName} has been open {daysWaiting} days, past its {slaDays}-day service level.',
 '/team/appraisals', 'WARNING', 1, 0, 1, NOW()),

(1, 'EXPORT_READY', 'Export ready',
 'Sent to the person who queued a large export',
 '{reportTitle} export is ready',
 'Your {format} export of {reportTitle} finished with {rowCount} rows.',
 '/reports', 'SUCCESS', 1, 0, 1, NOW());
