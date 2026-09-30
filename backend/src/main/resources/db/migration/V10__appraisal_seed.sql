-- =====================================================================
-- Phase 5 : default review workflow
--
-- The blueprint's four-stage flow, as configuration. An organisation that
-- does not do department-head sign-off can mark that step optional or
-- deactivate it, and the engine will step past it.
-- =====================================================================

INSERT INTO review_workflow_steps (company_id, step_order, step_type, name, description,
                                   is_required, sla_days, is_active, created_at) VALUES
(1, 10, 'SELF_ASSESSMENT', 'Self assessment',
 'The employee records what they achieved, what got in the way, and what they need next',
 1, 7, 1, NOW()),
(1, 20, 'MANAGER_REVIEW', 'Reporting manager review',
 'The manager rates competencies and writes the feedback that goes on record',
 1, 7, 1, NOW()),
(1, 30, 'DEPARTMENT_HEAD_REVIEW', 'Department head review',
 'The department head moderates across the team before it reaches HR',
 0, 5, 1, NOW()),
(1, 40, 'HR_REVIEW', 'HR review and sign-off',
 'HR checks consistency across departments and closes the appraisal',
 1, 5, 1, NOW());
