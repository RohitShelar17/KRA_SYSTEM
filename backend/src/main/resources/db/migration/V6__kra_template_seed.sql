-- =====================================================================
-- Phase 3 : starting KRA / KPI templates
--
-- Five role templates, each summing to exactly 100% across its KRAs, and
-- each KRA summing to 100% across its KPIs. Every row is editable; the point
-- is that a Sales scorecard and a QA scorecard are different data, not
-- different code.
--
-- Where a KPI can be measured from work already recorded, actual_source says
-- so, and the achievement engine fills it in from the timesheet or the task
-- board rather than waiting for someone to type a number.
-- =====================================================================

INSERT INTO kra_templates (company_id, code, name, description, designation_id, department_id,
                           period_type, is_published, is_active, created_at)
SELECT 1, v.code, v.name, v.description, d.id, dept.id, v.period_type, 1, 1, NOW()
FROM (
    SELECT 'DEV_TPL'   AS code, 'Software Engineer Scorecard' AS name,
           'Delivery, quality, defect resolution and collaboration for engineers' AS description,
           'SDE2' AS desig, 'SDE' AS dept, 'QUARTERLY' AS period_type UNION ALL
    SELECT 'QA_TPL',    'QA Engineer Scorecard',
           'Test execution, defect detection and documentation', 'QAE', 'QA', 'QUARTERLY' UNION ALL
    SELECT 'SALES_TPL', 'Sales Executive Scorecard',
           'Revenue, client engagement, renewals and pipeline discipline', 'SE', 'SALES', 'MONTHLY' UNION ALL
    SELECT 'HR_TPL',    'HR Executive Scorecard',
           'Recruitment, engagement, payroll accuracy and records', 'HRE', 'HR', 'QUARTERLY' UNION ALL
    SELECT 'MGR_TPL',   'Engineering Manager Scorecard',
           'Team delivery, productivity, people development and stakeholders', 'EM', 'SDE', 'QUARTERLY'
) v
JOIN designations d   ON d.code = v.desig AND d.company_id = 1
JOIN departments  dept ON dept.code = v.dept AND dept.company_id = 1;

INSERT INTO kra_template_items (company_id, kra_template_id, name, description, weightage,
                                display_order, created_at)
SELECT 1, t.id, v.kra_name, v.kra_desc, v.weightage, v.ord, NOW()
FROM (
    SELECT 'DEV_TPL' AS tpl, 'Software Development' AS kra_name, 'Feature and module delivery' AS kra_desc, 30.00 AS weightage, 10 AS ord UNION ALL
    SELECT 'DEV_TPL', 'Code Quality',          'Review outcomes, coverage and escaped defects', 20.00, 20 UNION ALL
    SELECT 'DEV_TPL', 'Bug Resolution',        'Speed and durability of defect fixes',          20.00, 30 UNION ALL
    SELECT 'DEV_TPL', 'Timely Delivery',       'Commitments met on the agreed date',            20.00, 40 UNION ALL
    SELECT 'DEV_TPL', 'Team Collaboration',    'Reviews, mentoring and knowledge sharing',      10.00, 50 UNION ALL

    SELECT 'QA_TPL',  'Test Execution',        'Coverage and throughput of testing',            35.00, 10 UNION ALL
    SELECT 'QA_TPL',  'Defect Detection',      'Finding defects before the customer does',      30.00, 20 UNION ALL
    SELECT 'QA_TPL',  'Timely Delivery',       'Test cycles finished on schedule',              20.00, 30 UNION ALL
    SELECT 'QA_TPL',  'Documentation',         'Test cases and evidence kept current',          15.00, 40 UNION ALL

    SELECT 'SALES_TPL', 'Revenue Generation',  'Closed business against target',                40.00, 10 UNION ALL
    SELECT 'SALES_TPL', 'Client Engagement',   'Reach and quality of client contact',           25.00, 20 UNION ALL
    SELECT 'SALES_TPL', 'Renewals',            'Retaining existing business',                   20.00, 30 UNION ALL
    SELECT 'SALES_TPL', 'Pipeline Hygiene',    'Responsiveness and CRM discipline',             15.00, 40 UNION ALL

    SELECT 'HR_TPL',  'Recruitment',           'Filling open roles well and quickly',           35.00, 10 UNION ALL
    SELECT 'HR_TPL',  'Employee Engagement',   'Engagement and retention outcomes',             25.00, 20 UNION ALL
    SELECT 'HR_TPL',  'Payroll & Compliance',  'Accuracy and statutory timeliness',             25.00, 30 UNION ALL
    SELECT 'HR_TPL',  'Documentation',         'Employee records kept accurate',                15.00, 40 UNION ALL

    SELECT 'MGR_TPL', 'Team Delivery',         'What the team shipped, and on time',            35.00, 10 UNION ALL
    SELECT 'MGR_TPL', 'Team Productivity',     'Productive hours and log compliance',           25.00, 20 UNION ALL
    SELECT 'MGR_TPL', 'People Development',    'Coaching, one-to-ones and reviews',             20.00, 30 UNION ALL
    SELECT 'MGR_TPL', 'Stakeholder Management','Escalations and responsiveness',                20.00, 40
) v
JOIN kra_templates t ON t.code = v.tpl AND t.company_id = 1;

INSERT INTO kpi_template_items (company_id, kra_template_item_id, name, description, measurement_type,
                                target_direction, unit, default_target_value, weightage, cap_percent,
                                actual_source, display_order, created_at)
SELECT 1, k.id, v.kpi_name, v.kpi_desc, v.measurement_type, v.direction, v.unit,
       v.target_value, v.weightage, 120.00, v.actual_source, v.ord, NOW()
FROM (
    -- Software Engineer
    SELECT 'DEV_TPL' AS tpl, 'Software Development' AS kra, 'Feature Delivery Rate' AS kpi_name,
           'Assigned features completed in the period' AS kpi_desc, 'PERCENTAGE' AS measurement_type,
           'HIGHER_IS_BETTER' AS direction, '%' AS unit, 95.00 AS target_value, 50.00 AS weightage,
           'TASK_COMPLETION_RATE' AS actual_source, 10 AS ord UNION ALL
    SELECT 'DEV_TPL','Software Development','Productive Hours Ratio','Productive time as a share of logged time','PERCENTAGE','HIGHER_IS_BETTER','%',85.00,30.00,'WORK_LOG_PRODUCTIVE_HOURS',20 UNION ALL
    SELECT 'DEV_TPL','Software Development','Story Points Delivered','Points accepted in the period','COUNT','HIGHER_IS_BETTER','pts',40.00,20.00,'MANUAL',30 UNION ALL

    SELECT 'DEV_TPL','Code Quality','Code Review Rejection Rate','Share of pull requests sent back','PERCENTAGE','LOWER_IS_BETTER','%',10.00,40.00,'MANUAL',10 UNION ALL
    SELECT 'DEV_TPL','Code Quality','Unit Test Coverage','Line coverage on owned modules','PERCENTAGE','HIGHER_IS_BETTER','%',80.00,40.00,'MANUAL',20 UNION ALL
    SELECT 'DEV_TPL','Code Quality','Critical Defects Escaped','Critical defects found after release','COUNT','LOWER_IS_BETTER','defects',1.00,20.00,'MANUAL',30 UNION ALL

    SELECT 'DEV_TPL','Bug Resolution','Bug Resolution Rate','Assigned bugs resolved in the period','PERCENTAGE','HIGHER_IS_BETTER','%',90.00,40.00,'MANUAL',10 UNION ALL
    SELECT 'DEV_TPL','Bug Resolution','Average Bug Resolution Time','Mean time from assignment to fix','HOURS','LOWER_IS_BETTER','hrs',24.00,30.00,'MANUAL',20 UNION ALL
    SELECT 'DEV_TPL','Bug Resolution','Reopened Bug Percentage','Fixes that came back','PERCENTAGE','LOWER_IS_BETTER','%',5.00,30.00,'MANUAL',30 UNION ALL

    SELECT 'DEV_TPL','Timely Delivery','On-time Task Completion','Tasks closed on or before the due date','PERCENTAGE','HIGHER_IS_BETTER','%',90.00,60.00,'TASK_ON_TIME_RATE',10 UNION ALL
    SELECT 'DEV_TPL','Timely Delivery','Sprint Commitment Met','Committed scope delivered','PERCENTAGE','HIGHER_IS_BETTER','%',85.00,40.00,'MANUAL',20 UNION ALL

    SELECT 'DEV_TPL','Team Collaboration','Peer Reviews Completed','Reviews performed for others','COUNT','HIGHER_IS_BETTER','reviews',20.00,50.00,'MANUAL',10 UNION ALL
    SELECT 'DEV_TPL','Team Collaboration','Knowledge Sharing Sessions','Sessions run for the team','COUNT','HIGHER_IS_BETTER','sessions',1.00,50.00,'MANUAL',20 UNION ALL

    -- QA Engineer
    SELECT 'QA_TPL','Test Execution','Test Case Execution Rate','Planned cases executed','PERCENTAGE','HIGHER_IS_BETTER','%',95.00,50.00,'TASK_COMPLETION_RATE',10 UNION ALL
    SELECT 'QA_TPL','Test Execution','Automation Coverage','Cases covered by automation','PERCENTAGE','HIGHER_IS_BETTER','%',60.00,30.00,'MANUAL',20 UNION ALL
    SELECT 'QA_TPL','Test Execution','Productive Hours Ratio','Productive time as a share of logged time','PERCENTAGE','HIGHER_IS_BETTER','%',85.00,20.00,'WORK_LOG_PRODUCTIVE_HOURS',30 UNION ALL

    SELECT 'QA_TPL','Defect Detection','Defect Detection Efficiency','Defects found before release','PERCENTAGE','HIGHER_IS_BETTER','%',90.00,60.00,'MANUAL',10 UNION ALL
    SELECT 'QA_TPL','Defect Detection','Critical Defects Missed','Critical defects that reached production','COUNT','LOWER_IS_BETTER','defects',1.00,40.00,'MANUAL',20 UNION ALL

    SELECT 'QA_TPL','Timely Delivery','On-time Cycle Completion','Test cycles finished by the due date','PERCENTAGE','HIGHER_IS_BETTER','%',90.00,100.00,'TASK_ON_TIME_RATE',10 UNION ALL

    SELECT 'QA_TPL','Documentation','Test Case Documentation','Cases documented to standard','PERCENTAGE','HIGHER_IS_BETTER','%',95.00,100.00,'MANUAL',10 UNION ALL

    -- Sales Executive
    SELECT 'SALES_TPL','Revenue Generation','Revenue Closed','Business closed against target','CURRENCY','HIGHER_IS_BETTER','INR',1000000.00,70.00,'MANUAL',10 UNION ALL
    SELECT 'SALES_TPL','Revenue Generation','New Clients Acquired','First-time clients onboarded','COUNT','HIGHER_IS_BETTER','clients',5.00,30.00,'MANUAL',20 UNION ALL

    SELECT 'SALES_TPL','Client Engagement','Client Calls Completed','Calls logged in the period','COUNT','HIGHER_IS_BETTER','calls',100.00,50.00,'TASK_COMPLETION_RATE',10 UNION ALL
    SELECT 'SALES_TPL','Client Engagement','Meeting Conversion Rate','Meetings that became proposals','PERCENTAGE','HIGHER_IS_BETTER','%',25.00,50.00,'MANUAL',20 UNION ALL

    SELECT 'SALES_TPL','Renewals','Renewal Rate','Policies renewed on time','PERCENTAGE','HIGHER_IS_BETTER','%',85.00,100.00,'MANUAL',10 UNION ALL

    SELECT 'SALES_TPL','Pipeline Hygiene','Proposal Turnaround','Time from request to proposal','HOURS','LOWER_IS_BETTER','hrs',48.00,50.00,'MANUAL',10 UNION ALL
    SELECT 'SALES_TPL','Pipeline Hygiene','CRM Update Compliance','Opportunities kept current','PERCENTAGE','HIGHER_IS_BETTER','%',95.00,50.00,'MANUAL',20 UNION ALL

    -- HR Executive
    SELECT 'HR_TPL','Recruitment','Positions Closed','Open roles filled in the period','COUNT','HIGHER_IS_BETTER','roles',6.00,50.00,'MANUAL',10 UNION ALL
    SELECT 'HR_TPL','Recruitment','Time to Hire','Days from requisition to acceptance','NUMERIC','LOWER_IS_BETTER','days',30.00,50.00,'MANUAL',20 UNION ALL

    SELECT 'HR_TPL','Employee Engagement','Engagement Score','Pulse survey average','RATING','HIGHER_IS_BETTER','of 5',4.00,60.00,'MANUAL',10 UNION ALL
    SELECT 'HR_TPL','Employee Engagement','Attrition Rate','Regretted attrition in the period','PERCENTAGE','LOWER_IS_BETTER','%',10.00,40.00,'MANUAL',20 UNION ALL

    SELECT 'HR_TPL','Payroll & Compliance','Payroll Accuracy','Payslips issued without correction','PERCENTAGE','HIGHER_IS_BETTER','%',99.00,60.00,'MANUAL',10 UNION ALL
    SELECT 'HR_TPL','Payroll & Compliance','Statutory Filings On Time','All filings made by the due date','BOOLEAN','BOOLEAN',NULL,1.00,40.00,'MANUAL',20 UNION ALL

    SELECT 'HR_TPL','Documentation','Employee Records Current','Records complete and up to date','PERCENTAGE','HIGHER_IS_BETTER','%',98.00,100.00,'MANUAL',10 UNION ALL

    -- Engineering Manager
    SELECT 'MGR_TPL','Team Delivery','Team Task Completion','Team tasks completed in the period','PERCENTAGE','HIGHER_IS_BETTER','%',90.00,60.00,'TASK_COMPLETION_RATE',10 UNION ALL
    SELECT 'MGR_TPL','Team Delivery','Team On-time Delivery','Team tasks closed by the due date','PERCENTAGE','HIGHER_IS_BETTER','%',85.00,40.00,'TASK_ON_TIME_RATE',20 UNION ALL

    SELECT 'MGR_TPL','Team Productivity','Team Productive Hours Ratio','Productive share of logged time','PERCENTAGE','HIGHER_IS_BETTER','%',85.00,60.00,'WORK_LOG_PRODUCTIVE_HOURS',10 UNION ALL
    SELECT 'MGR_TPL','Team Productivity','Work Log Submission Compliance','Days filed by the team','PERCENTAGE','HIGHER_IS_BETTER','%',95.00,40.00,'MANUAL',20 UNION ALL

    SELECT 'MGR_TPL','People Development','One-to-ones Conducted','Sessions held with reports','COUNT','HIGHER_IS_BETTER','sessions',8.00,50.00,'MANUAL',10 UNION ALL
    SELECT 'MGR_TPL','People Development','Reviews Completed On Time','Reviews filed inside the window','PERCENTAGE','HIGHER_IS_BETTER','%',100.00,50.00,'MANUAL',20 UNION ALL

    SELECT 'MGR_TPL','Stakeholder Management','Escalations Resolved','Escalations closed in the period','PERCENTAGE','HIGHER_IS_BETTER','%',95.00,60.00,'MANUAL',10 UNION ALL
    SELECT 'MGR_TPL','Stakeholder Management','Stakeholder Response Time','Mean first response','HOURS','LOWER_IS_BETTER','hrs',8.00,40.00,'MANUAL',20
) v
JOIN kra_templates t      ON t.code = v.tpl AND t.company_id = 1
JOIN kra_template_items k ON k.kra_template_id = t.id AND k.name = v.kra;
