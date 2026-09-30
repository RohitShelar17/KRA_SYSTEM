-- =====================================================================
-- Phase 1 : reference data seed
-- Departments, designations and roles are ordinary rows, not code
-- constants: everything below can be edited or removed by an administrator.
-- The bootstrap administrator password is set on first start by
-- BootstrapAdminInitializer from ADMIN_DEFAULT_PASSWORD; no password hash
-- is ever committed to a migration.
-- =====================================================================

INSERT INTO companies (id, name, code, legal_name, timezone, currency_code, is_active, created_at)
VALUES (1, 'Share India', 'SHAREINDIA', 'Share India Securities Limited', 'Asia/Kolkata', 'INR', 1, NOW());

INSERT INTO company_settings (company_id, work_start_time, work_end_time, break_minutes, working_days,
                              worklog_approval_enabled, overtime_allowed, max_overtime_minutes,
                              backdate_window_days, worklog_cutoff_time, min_submission_rate_pct, created_at)
VALUES (1, '09:00:00', '18:00:00', 60, 'MON,TUE,WED,THU,FRI', 1, 1, 180, 3, '20:00:00', 80.00, NOW());

-- ---------------------------------------------------------------------
-- Permissions
-- ---------------------------------------------------------------------
INSERT INTO permissions (code, name, module, description, created_at) VALUES
('DASHBOARD_VIEW',          'View dashboard',              'DASHBOARD',   'Open the role dashboard', NOW()),
('WORKLOG_WRITE_SELF',      'Record own work',             'WORKLOG',     'Create and edit own daily work log', NOW()),
('WORKLOG_READ_TEAM',       'View team work logs',         'WORKLOG',     'Read work logs within data scope', NOW()),
('WORKLOG_APPROVE',         'Approve work logs',           'WORKLOG',     'Approve, reject or return a submitted day', NOW()),
('TASK_READ_SELF',          'View own tasks',              'TASK',        'Read tasks assigned to self', NOW()),
('TASK_READ_TEAM',          'View team tasks',             'TASK',        'Read tasks within data scope', NOW()),
('TASK_ASSIGN',             'Assign tasks',                'TASK',        'Create and assign tasks to others', NOW()),
('KRA_READ_SELF',           'View own KRAs',               'KRA',         'Read own assigned KRAs and KPIs', NOW()),
('KRA_READ_TEAM',           'View team KRAs',              'KRA',         'Read KRAs within data scope', NOW()),
('KRA_TEMPLATE_MANAGE',     'Manage KRA templates',        'KRA',         'Create and edit KRA and KPI templates', NOW()),
('KRA_ASSIGN',              'Assign KRAs',                 'KRA',         'Assign templates and adjust weightages', NOW()),
('KPI_MANAGE',              'Manage KPI targets',          'KPI',         'Set KPI targets and record actuals', NOW()),
('PERFORMANCE_READ_SELF',   'View own performance',        'PERFORMANCE', 'Read own performance snapshots', NOW()),
('PERFORMANCE_READ_TEAM',   'View team performance',       'PERFORMANCE', 'Read performance within data scope', NOW()),
('PERFORMANCE_READ_ALL',    'View all performance',        'PERFORMANCE', 'Read performance across the organisation', NOW()),
('PERFORMANCE_REVIEW',      'Review performance',          'PERFORMANCE', 'Submit manager or head evaluations', NOW()),
('PERFORMANCE_ADJUST',      'Adjust performance scores',   'PERFORMANCE', 'Manual score adjustment with reason', NOW()),
('PERFORMANCE_FINALIZE',    'Finalise performance',        'PERFORMANCE', 'Freeze a performance snapshot', NOW()),
('PERFORMANCE_REOPEN',      'Reopen performance',          'PERFORMANCE', 'Unfreeze a finalised snapshot with reason', NOW()),
('SELF_ASSESSMENT_SUBMIT',  'Submit self assessment',      'APPRAISAL',   'Complete the employee self assessment', NOW()),
('EMPLOYEE_READ',           'View employees',              'EMPLOYEE',    'Read employee records within data scope', NOW()),
('EMPLOYEE_MANAGE',         'Manage employees',            'EMPLOYEE',    'Create, update and deactivate employees', NOW()),
('DEPARTMENT_READ',         'View departments',            'ORGANISATION','Read departments and teams', NOW()),
('DEPARTMENT_MANAGE',       'Manage departments',          'ORGANISATION','Create and edit departments', NOW()),
('TEAM_MANAGE',             'Manage teams',                'ORGANISATION','Create and edit teams', NOW()),
('DESIGNATION_MANAGE',      'Manage designations',         'ORGANISATION','Create and edit designations', NOW()),
('ROLE_MANAGE',             'Manage roles',                'SECURITY',    'Edit roles and their permissions', NOW()),
('SETTINGS_MANAGE',         'Manage settings',             'SETTINGS',    'Company settings, holidays, formulas, bands', NOW()),
('REPORT_VIEW',             'View reports',                'REPORT',      'Run reports within data scope', NOW()),
('REPORT_EXPORT',           'Export reports',              'REPORT',      'Export report results to file', NOW()),
('NOTIFICATION_READ',       'Read notifications',          'NOTIFICATION','Read own notification feed', NOW()),
('AUDIT_READ',              'Read audit logs',             'AUDIT',       'Search the audit trail', NOW());

-- ---------------------------------------------------------------------
-- Roles
-- ---------------------------------------------------------------------
INSERT INTO roles (company_id, code, name, description, data_scope, is_system_role, is_active, created_at) VALUES
(1, 'SUPER_ADMIN',       'Super Admin',       'Full control of the platform',                      'ORGANISATION', 1, 1, NOW()),
(1, 'HR_ADMIN',          'HR Admin',          'People, cycles, templates and finalisation',        'ORGANISATION', 1, 1, NOW()),
(1, 'DEPARTMENT_HEAD',   'Department Head',   'Owns a department and its performance',             'DEPARTMENT',   1, 1, NOW()),
(1, 'REPORTING_MANAGER', 'Reporting Manager', 'Reviews and rates direct and indirect reports',     'REPORTS',      1, 1, NOW()),
(1, 'EMPLOYEE',          'Employee',          'Records daily work and views own performance',      'SELF',         1, 1, NOW());

-- Super Admin: every permission
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'SUPER_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'DASHBOARD_VIEW','WORKLOG_READ_TEAM','TASK_READ_TEAM','KRA_READ_TEAM','KRA_TEMPLATE_MANAGE','KRA_ASSIGN',
    'KPI_MANAGE','PERFORMANCE_READ_SELF','PERFORMANCE_READ_ALL','PERFORMANCE_REVIEW','PERFORMANCE_ADJUST',
    'PERFORMANCE_FINALIZE','PERFORMANCE_REOPEN','EMPLOYEE_READ','EMPLOYEE_MANAGE','DEPARTMENT_READ',
    'DEPARTMENT_MANAGE','TEAM_MANAGE','DESIGNATION_MANAGE','SETTINGS_MANAGE','REPORT_VIEW','REPORT_EXPORT',
    'NOTIFICATION_READ','AUDIT_READ'
) WHERE r.code = 'HR_ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'DASHBOARD_VIEW','WORKLOG_WRITE_SELF','WORKLOG_READ_TEAM','WORKLOG_APPROVE','TASK_READ_SELF','TASK_READ_TEAM',
    'TASK_ASSIGN','KRA_READ_SELF','KRA_READ_TEAM','KRA_TEMPLATE_MANAGE','KRA_ASSIGN','KPI_MANAGE',
    'PERFORMANCE_READ_SELF','PERFORMANCE_READ_TEAM','PERFORMANCE_REVIEW','SELF_ASSESSMENT_SUBMIT',
    'EMPLOYEE_READ','DEPARTMENT_READ','TEAM_MANAGE','REPORT_VIEW','REPORT_EXPORT','NOTIFICATION_READ'
) WHERE r.code = 'DEPARTMENT_HEAD';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'DASHBOARD_VIEW','WORKLOG_WRITE_SELF','WORKLOG_READ_TEAM','WORKLOG_APPROVE','TASK_READ_SELF','TASK_READ_TEAM',
    'TASK_ASSIGN','KRA_READ_SELF','KRA_READ_TEAM','KRA_ASSIGN','KPI_MANAGE','PERFORMANCE_READ_SELF',
    'PERFORMANCE_READ_TEAM','PERFORMANCE_REVIEW','SELF_ASSESSMENT_SUBMIT','EMPLOYEE_READ','DEPARTMENT_READ',
    'REPORT_VIEW','NOTIFICATION_READ'
) WHERE r.code = 'REPORTING_MANAGER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'DASHBOARD_VIEW','WORKLOG_WRITE_SELF','TASK_READ_SELF','KRA_READ_SELF','PERFORMANCE_READ_SELF',
    'SELF_ASSESSMENT_SUBMIT','DEPARTMENT_READ','NOTIFICATION_READ'
) WHERE r.code = 'EMPLOYEE';

-- ---------------------------------------------------------------------
-- Departments (starting set - fully editable)
-- ---------------------------------------------------------------------
INSERT INTO departments (company_id, code, name, description, is_active, created_at) VALUES
(1, 'IT',       'IT',                   'Infrastructure, systems and internal support', 1, NOW()),
(1, 'SDE',      'Software Development', 'Product and platform engineering',             1, NOW()),
(1, 'QA',       'Quality Assurance',    'Manual and automated testing',                 1, NOW()),
(1, 'HR',       'Human Resources',      'Talent, engagement and people operations',     1, NOW()),
(1, 'FIN',      'Finance',              'Financial planning and control',               1, NOW()),
(1, 'ACC',      'Accounts',             'Bookkeeping, payables and receivables',        1, NOW()),
(1, 'SALES',    'Sales',                'Revenue generation and client acquisition',    1, NOW()),
(1, 'MKT',      'Marketing',            'Brand, demand generation and communications',  1, NOW()),
(1, 'OPS',      'Operations',           'Day to day business operations',               1, NOW()),
(1, 'CS',       'Customer Support',     'Client servicing and issue resolution',        1, NOW()),
(1, 'INS',      'Insurance',            'Insurance advisory and policy servicing',      1, NOW()),
(1, 'LEGAL',    'Legal',                'Contracts, litigation and advisory',           1, NOW()),
(1, 'COMP',     'Compliance',           'Regulatory compliance and risk',               1, NOW());

-- ---------------------------------------------------------------------
-- Designations
-- ---------------------------------------------------------------------
INSERT INTO designations (company_id, code, name, grade_level, department_id, is_active, created_at)
SELECT 1, v.code, v.name, v.grade, d.id, 1, NOW()
FROM (
    SELECT 'SDE1'   AS code, 'Software Engineer'          AS name, 'L2' AS grade, 'SDE'   AS dept UNION ALL
    SELECT 'SDE2',          'Senior Software Engineer',         'L3',        'SDE'   UNION ALL
    SELECT 'TL',            'Tech Lead',                        'L5',        'SDE'   UNION ALL
    SELECT 'EM',            'Engineering Manager',              'L6',        'SDE'   UNION ALL
    SELECT 'QAE',           'QA Engineer',                      'L2',        'QA'    UNION ALL
    SELECT 'QAL',           'QA Lead',                          'L4',        'QA'    UNION ALL
    SELECT 'HRE',           'HR Executive',                     'L2',        'HR'    UNION ALL
    SELECT 'HRM',           'HR Manager',                       'L5',        'HR'    UNION ALL
    SELECT 'SE',            'Sales Executive',                  'L2',        'SALES' UNION ALL
    SELECT 'SM',            'Sales Manager',                    'L5',        'SALES' UNION ALL
    SELECT 'MKE',           'Marketing Executive',              'L2',        'MKT'   UNION ALL
    SELECT 'OPE',           'Operations Executive',             'L2',        'OPS'   UNION ALL
    SELECT 'CSE',           'Customer Support Executive',       'L2',        'CS'    UNION ALL
    SELECT 'INSA',          'Insurance Advisor',                'L3',        'INS'   UNION ALL
    SELECT 'CMPO',          'Compliance Officer',               'L4',        'COMP'  UNION ALL
    SELECT 'SYSAD',         'System Administrator',             'L3',        'IT'
) v
JOIN departments d ON d.code = v.dept AND d.company_id = 1;

-- ---------------------------------------------------------------------
-- Bootstrap administrator
-- password_hash is intentionally empty; BootstrapAdminInitializer encodes
-- ADMIN_DEFAULT_PASSWORD on first start and flips the status to ACTIVE.
-- ---------------------------------------------------------------------
INSERT INTO users (company_id, username, email, password_hash, status, must_change_password, is_active, created_at)
VALUES (1, 'admin', 'admin@company.com', '', 'PENDING_ACTIVATION', 1, 1, NOW());

INSERT INTO user_roles (user_id, role_id, assigned_at)
SELECT u.id, r.id, NOW() FROM users u JOIN roles r ON r.code = 'SUPER_ADMIN' WHERE u.username = 'admin';

INSERT INTO employees (company_id, user_id, employee_code, first_name, last_name, email, department_id,
                       designation_id, joining_date, employment_type, work_location, work_mode, status,
                       is_active, created_at)
SELECT 1, u.id, 'EMP-0001', 'System', 'Administrator', 'admin@company.com', d.id, g.id,
       CURDATE(), 'FULL_TIME', 'Head Office', 'OFFICE', 'ACTIVE', 1, NOW()
FROM users u
JOIN departments  d ON d.code = 'IT'    AND d.company_id = 1
JOIN designations g ON g.code = 'SYSAD' AND g.company_id = 1
WHERE u.username = 'admin';
