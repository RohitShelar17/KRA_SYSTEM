-- =====================================================================
-- Phase 4 : default formula and rating bands
--
-- These are the blueprint's starting weights. Every one is editable, and
-- saving a change creates a new version rather than mutating this one, so
-- existing snapshots keep the formula they were computed under.
-- =====================================================================

INSERT INTO performance_formula_config (company_id, scope_type, version, name, notes,
                                        min_submission_rate_pct, submission_penalty_pct,
                                        is_active, effective_from, created_at) VALUES
(1, 'DAILY',     1, 'Daily performance v1',
 'Task delivery, goal progress and how the day was actually spent', 0.00, 0.00, 1, '2026-01-01', NOW()),
(1, 'MONTHLY',   1, 'Monthly performance v1',
 'Daily consistency plus goal achievement and the manager view', 80.00, 5.00, 1, '2026-01-01', NOW()),
(1, 'QUARTERLY', 1, 'Quarterly performance v1',
 'Rolls up the three months with quarterly goal achievement', 0.00, 0.00, 1, '2026-01-01', NOW()),
(1, 'ANNUAL',    1, 'Annual performance v1',
 'Rolls up four quarters with annual goals and sign-off ratings', 0.00, 0.00, 1, '2026-01-01', NOW());

INSERT INTO performance_formula_parameters (company_id, config_id, parameter_code, label, weightage,
                                            display_order, created_at)
SELECT 1, c.id, v.code, v.label, v.weightage, v.ord, NOW()
FROM (
    SELECT 'DAILY' AS scope, 'TASK_COMPLETION'  AS code, 'Task completion'        AS label, 30.00 AS weightage, 10 AS ord UNION ALL
    SELECT 'DAILY',          'KRA_ACHIEVEMENT',        'KRA achievement',              25.00, 20 UNION ALL
    SELECT 'DAILY',          'PRODUCTIVITY',           'Productive hours',             20.00, 30 UNION ALL
    SELECT 'DAILY',          'QUALITY_RATING',         'Quality rating',               15.00, 40 UNION ALL
    SELECT 'DAILY',          'MANAGER_RATING',         'Manager rating',               10.00, 50 UNION ALL

    SELECT 'MONTHLY',        'DAILY_AVERAGE',          'Average daily score',          40.00, 10 UNION ALL
    SELECT 'MONTHLY',        'KRA_ACHIEVEMENT',        'KRA achievement',              25.00, 20 UNION ALL
    SELECT 'MONTHLY',        'KPI_ACHIEVEMENT',        'KPI achievement',              20.00, 30 UNION ALL
    SELECT 'MONTHLY',        'MANAGER_RATING',         'Manager rating',               15.00, 40 UNION ALL

    SELECT 'QUARTERLY',      'MONTHLY_AVERAGE',        'Average monthly score',        60.00, 10 UNION ALL
    SELECT 'QUARTERLY',      'KPI_ACHIEVEMENT',        'Quarterly KPI achievement',    25.00, 20 UNION ALL
    SELECT 'QUARTERLY',      'MANAGER_RATING',         'Manager rating',               15.00, 30 UNION ALL

    SELECT 'ANNUAL',         'QUARTERLY_AVERAGE',      'Average quarterly score',      50.00, 10 UNION ALL
    SELECT 'ANNUAL',         'KRA_ACHIEVEMENT',        'Annual KRA achievement',       20.00, 20 UNION ALL
    SELECT 'ANNUAL',         'KPI_ACHIEVEMENT',        'Annual KPI achievement',       15.00, 30 UNION ALL
    SELECT 'ANNUAL',         'MANAGER_RATING',         'Manager rating',               10.00, 40 UNION ALL
    SELECT 'ANNUAL',         'DEPARTMENT_HEAD_RATING', 'Department head rating',        5.00, 50
) v
JOIN performance_formula_config c ON c.scope_type = v.scope AND c.company_id = 1 AND c.version = 1;

-- Contiguous across 0-100 so no score can land between two bands.
INSERT INTO performance_rating_bands (company_id, name, description, min_score, max_score,
                                      colour_hex, display_order, is_active, created_at) VALUES
(1, 'Outstanding',        'Consistently exceeds every target',      90.00, 200.00, '#059669', 10, 1, NOW()),
(1, 'Excellent',          'Exceeds most targets',                   80.00,  89.99, '#10B981', 20, 1, NOW()),
(1, 'Good',               'Meets targets and exceeds some',         70.00,  79.99, '#0EA5E9', 30, 1, NOW()),
(1, 'Meets Expectations', 'Meets the requirements of the role',     60.00,  69.99, '#6366F1', 40, 1, NOW()),
(1, 'Needs Improvement',  'An improvement plan is required',        50.00,  59.99, '#F59E0B', 50, 1, NOW()),
(1, 'Unsatisfactory',     'A formal review is required',             0.00,  49.99, '#DC2626', 60, 1, NOW());
