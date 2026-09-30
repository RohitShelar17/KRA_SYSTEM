-- =====================================================================
-- Phase 2 : activity catalogue seed
-- A starting set of categories and department task templates. Every row is
-- editable: the point is that a Sales day and a QA day are described in
-- their own vocabulary, configured rather than coded.
-- =====================================================================

INSERT INTO task_categories (company_id, code, name, description, category_type, is_productive,
                             department_id, colour_hex, display_order, is_active, created_at) VALUES
(1, 'DEVELOPMENT',   'Development',          'Feature and module development',        'WORK',     1, NULL, '#4338CA', 10, 1, NOW()),
(1, 'BUG_FIXING',    'Bug Fixing',           'Defect investigation and resolution',   'WORK',     1, NULL, '#0EA5E9', 20, 1, NOW()),
(1, 'CODE_REVIEW',   'Code Review',          'Reviewing and approving changes',       'WORK',     1, NULL, '#6366F1', 30, 1, NOW()),
(1, 'TESTING',       'Testing',              'Manual and automated testing',          'WORK',     1, NULL, '#10B981', 40, 1, NOW()),
(1, 'DEPLOYMENT',    'Deployment',           'Release and deployment activity',       'WORK',     1, NULL, '#8B5CF6', 50, 1, NOW()),
(1, 'DOCUMENTATION', 'Documentation',        'Writing specs, notes and handovers',    'WORK',     1, NULL, '#0891B2', 60, 1, NOW()),
(1, 'ANALYSIS',      'Requirement Analysis', 'Understanding and shaping requirements','WORK',     1, NULL, '#7C3AED', 70, 1, NOW()),
(1, 'CLIENT_WORK',   'Client Work',          'Direct client servicing and delivery',  'WORK',     1, NULL, '#2563EB', 80, 1, NOW()),
(1, 'MEETING',       'Meeting',              'Standups, reviews and discussions',     'MEETING',  1, NULL, '#F59E0B', 90, 1, NOW()),
(1, 'CLIENT_CALL',   'Client Call',          'Calls and demos with clients',          'MEETING',  1, NULL, '#D97706', 100, 1, NOW()),
(1, 'TRAINING',      'Training',             'Learning, courses and knowledge sharing','TRAINING',1, NULL, '#059669', 110, 1, NOW()),
(1, 'SUPPORT',       'Support',              'Production support and escalations',    'SUPPORT',  1, NULL, '#DC2626', 120, 1, NOW()),
(1, 'ADMIN',         'Administrative',       'Timesheets, approvals and internal admin','ADMIN',  0, NULL, '#64748B', 130, 1, NOW()),
(1, 'BREAK',         'Break',                'Lunch and rest breaks',                 'BREAK',    0, NULL, '#94A3B8', 140, 1, NOW());

-- Department task templates. Category is resolved by code so the seed does
-- not depend on generated ids.
INSERT INTO task_templates (company_id, department_id, task_category_id, name, description,
                            default_estimated_minutes, default_priority, recurrence, display_order,
                            is_active, created_at)
SELECT 1, d.id, c.id, v.template_name, v.template_desc, v.minutes, v.priority, v.recurrence, v.ord, 1, NOW()
FROM (
    SELECT 'SDE' AS dept, 'DEVELOPMENT'   AS cat, 'Feature Development'        AS template_name, 'Build an assigned feature or module'          AS template_desc, 240 AS minutes, 'HIGH'   AS priority, 'NONE'   AS recurrence, 10 AS ord UNION ALL
    SELECT 'SDE', 'BUG_FIXING',    'Bug Fixing',                'Investigate and resolve reported defects',    120, 'HIGH',     'NONE',   20 UNION ALL
    SELECT 'SDE', 'DEVELOPMENT',   'API Integration',           'Integrate an internal or third-party API',    180, 'MEDIUM',   'NONE',   30 UNION ALL
    SELECT 'SDE', 'CODE_REVIEW',   'Code Review',               'Review pull requests from the team',           60, 'MEDIUM',   'DAILY',  40 UNION ALL
    SELECT 'SDE', 'DEPLOYMENT',    'Deployment',                'Release to staging or production',             90, 'HIGH',     'NONE',   50 UNION ALL
    SELECT 'SDE', 'DOCUMENTATION', 'Technical Documentation',   'Write or update technical documentation',      60, 'LOW',      'NONE',   60 UNION ALL
    SELECT 'SDE', 'ANALYSIS',      'Requirement Analysis',      'Analyse and clarify incoming requirements',   120, 'MEDIUM',   'NONE',   70 UNION ALL
    SELECT 'SDE', 'MEETING',       'Daily Standup',             'Team standup',                                 15, 'MEDIUM',   'DAILY',  80 UNION ALL

    SELECT 'QA',  'TESTING',       'Test Case Execution',       'Execute planned test cases',                  180, 'HIGH',     'NONE',   10 UNION ALL
    SELECT 'QA',  'TESTING',       'Regression Testing',        'Run the regression suite',                    240, 'HIGH',     'NONE',   20 UNION ALL
    SELECT 'QA',  'DOCUMENTATION', 'Test Case Writing',         'Author new test cases',                       120, 'MEDIUM',   'NONE',   30 UNION ALL
    SELECT 'QA',  'BUG_FIXING',    'Defect Verification',       'Verify and close fixed defects',               90, 'HIGH',     'NONE',   40 UNION ALL

    SELECT 'SALES','CLIENT_CALL',  'Client Calls',              'Outbound and inbound client calls',           120, 'HIGH',     'DAILY',  10 UNION ALL
    SELECT 'SALES','CLIENT_WORK',  'Lead Generation',           'Prospecting and lead qualification',          120, 'HIGH',     'DAILY',  20 UNION ALL
    SELECT 'SALES','CLIENT_CALL',  'Client Meeting',            'In-person or virtual client meeting',          60, 'HIGH',     'NONE',   30 UNION ALL
    SELECT 'SALES','CLIENT_WORK',  'Proposal Submission',       'Prepare and submit a proposal',                90, 'HIGH',     'NONE',   40 UNION ALL
    SELECT 'SALES','CLIENT_WORK',  'Follow-up',                 'Follow up on open opportunities',              60, 'MEDIUM',   'DAILY',  50 UNION ALL
    SELECT 'SALES','CLIENT_WORK',  'Policy Closure',            'Close and document a sale',                    60, 'CRITICAL', 'NONE',   60 UNION ALL
    SELECT 'SALES','CLIENT_WORK',  'Renewal Follow-up',         'Chase renewals due this cycle',                60, 'HIGH',     'WEEKLY', 70 UNION ALL

    SELECT 'HR',  'CLIENT_WORK',   'Recruitment',               'Sourcing, screening and interviews',          180, 'HIGH',     'NONE',   10 UNION ALL
    SELECT 'HR',  'ADMIN',         'Employee Onboarding',       'Onboard a new joiner',                        120, 'HIGH',     'NONE',   20 UNION ALL
    SELECT 'HR',  'ADMIN',         'Payroll Activities',        'Payroll inputs and verification',             180, 'CRITICAL', 'MONTHLY',30 UNION ALL
    SELECT 'HR',  'CLIENT_WORK',   'Employee Engagement',       'Engagement activities and check-ins',          90, 'MEDIUM',   'NONE',   40 UNION ALL
    SELECT 'HR',  'DOCUMENTATION', 'HR Documentation',          'Letters, records and policy updates',          60, 'MEDIUM',   'NONE',   50 UNION ALL

    SELECT 'CS',  'SUPPORT',       'Ticket Resolution',         'Resolve customer tickets',                    240, 'HIGH',     'DAILY',  10 UNION ALL
    SELECT 'CS',  'CLIENT_CALL',   'Customer Call',             'Inbound customer support calls',              120, 'HIGH',     'DAILY',  20 UNION ALL
    SELECT 'CS',  'SUPPORT',       'Escalation Handling',       'Handle escalated customer issues',             90, 'CRITICAL', 'NONE',   30 UNION ALL

    SELECT 'INS', 'CLIENT_WORK',   'Policy Servicing',          'Service existing policies',                   180, 'HIGH',     'DAILY',  10 UNION ALL
    SELECT 'INS', 'CLIENT_CALL',   'Client Advisory',           'Advisory calls on cover and claims',          120, 'HIGH',     'NONE',   20 UNION ALL
    SELECT 'INS', 'DOCUMENTATION', 'Claims Documentation',      'Prepare and verify claim documents',          120, 'HIGH',     'NONE',   30 UNION ALL

    SELECT 'COMP','ADMIN',         'Compliance Review',         'Review activity against regulation',          180, 'CRITICAL', 'WEEKLY', 10 UNION ALL
    SELECT 'COMP','DOCUMENTATION', 'Regulatory Filing',         'Prepare and submit regulatory filings',       240, 'CRITICAL', 'MONTHLY',20 UNION ALL

    SELECT 'IT',  'SUPPORT',       'Infrastructure Support',    'Systems, access and device support',          180, 'HIGH',     'DAILY',  10 UNION ALL
    SELECT 'IT',  'ADMIN',         'System Maintenance',        'Patching, backups and monitoring',            120, 'HIGH',     'WEEKLY', 20
) v
JOIN departments    d ON d.code = v.dept AND d.company_id = 1
JOIN task_categories c ON c.code = v.cat AND c.company_id = 1;

-- A sample internal project so the work log has something to attribute time to.
INSERT INTO projects (company_id, code, name, description, department_id, status, billable, is_active, created_at)
SELECT 1, 'INTERNAL', 'Internal Work', 'Default project for internal, non-client activity',
       NULL, 'ACTIVE', 0, 1, NOW();
