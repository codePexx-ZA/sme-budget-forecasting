-- Seeds FICTIONAL test data (one business, one user per role, income statement history, KPI targets) until real participant data is available.

BEGIN;

-- pgcrypto's crypt()/gen_salt('bf') produce BCrypt hashes ($2a$10$...) that Spring Security's BCryptPasswordEncoder can verify.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Fixed IDs so screens, API tests and teammates can refer to the same sample records.
INSERT INTO business (business_id, legal_name, industry, country, year_end_month) VALUES
    ('10000000000000000000000000000001', 'Sample Hardware Store (FICTIONAL TEST DATA)', 'Retail - hardware', 'ZAF', 2);

INSERT INTO business_unit (unit_id, business_id, unit_name, unit_type, currency) VALUES
    ('20000000000000000000000000000001', '10000000000000000000000000000001', 'Whole company', 'company', 'ZAR');

-- Test logins only. Every sample user's password is Test@1234; never reuse it for real accounts.
INSERT INTO app_user (user_id, unit_id, full_name, email, password_hash, role) VALUES
    ('30000000000000000000000000000001', '20000000000000000000000000000001', 'Sam Submitter (Test)',        'submitter@example.com',      crypt('Test@1234', gen_salt('bf', 10)), 'submitter'),
    ('30000000000000000000000000000002', '20000000000000000000000000000001', 'Alex Approver (Test)',        'approver@example.com',       crypt('Test@1234', gen_salt('bf', 10)), 'approver'),
    ('30000000000000000000000000000003', '20000000000000000000000000000001', 'Dana Decision Maker (Test)',  'decisionmaker@example.com',  crypt('Test@1234', gen_salt('bf', 10)), 'decision_maker');

-- Submission 1: approved annual history FY2022-FY2026 (February year-end, so each year starts 1 March).
-- This is the history the Python forecasting service reads.
INSERT INTO budget_submission (submission_id, unit_id, submitted_by, period_start, period_end, status, submitted_at, import_method, source_file_name) VALUES
    ('40000000000000000000000000000001', '20000000000000000000000000000001', '30000000000000000000000000000001',
     '2021-03-01', '2026-02-28', 'approved', '2026-04-10 09:00:00+02', 'manual', NULL);

-- Each array holds FY2022..FY2026 in order; expenses are positive amounts in rands.
INSERT INTO financial_line_item (submission_id, category, period_start, amount, frequency)
SELECT '40000000000000000000000000000001', v.category, make_date(2020 + y.n, 3, 1), v.amounts[y.n], 'annual'
FROM (VALUES
    ('revenue',         ARRAY[4200000, 4480000, 4810000, 5090000, 5420000]::numeric[]),
    ('other_income',    ARRAY[  35000,   38000,   41000,   39000,   44000]::numeric[]),
    ('cost_of_sales',   ARRAY[2520000, 2700000, 2910000, 3060000, 3250000]::numeric[]),
    ('employee_costs',  ARRAY[ 760000,  810000,  860000,  915000,  975000]::numeric[]),
    ('occupancy',       ARRAY[ 240000,  252000,  265000,  278000,  292000]::numeric[]),
    ('electricity',     ARRAY[  72000,   81000,   94000,  106000,  118000]::numeric[]),
    ('other_utilities', ARRAY[  30000,   32000,   34000,   36000,   38000]::numeric[]),
    ('transport_fuel',  ARRAY[  88000,  104000,  112000,  109000,  116000]::numeric[]),
    ('marketing',       ARRAY[  45000,   48000,   52000,   55000,   58000]::numeric[]),
    ('other_operating', ARRAY[ 120000,  126000,  131000,  138000,  145000]::numeric[]),
    ('depreciation',    ARRAY[  65000,   68000,   70000,   72000,   75000]::numeric[]),
    ('finance_costs',   ARRAY[  28000,   31000,   33000,   30000,   27000]::numeric[]),
    ('income_tax',      ARRAY[  72090,   71820,   78300,   89100,   99900]::numeric[])
) AS v(category, amounts)
CROSS JOIN generate_series(1, 5) AS y(n);

INSERT INTO approval_action (submission_id, acted_by, decision, comments, acted_at) VALUES
    ('40000000000000000000000000000001', '30000000000000000000000000000002', 'approved',
     'Checked against the signed annual financial statements.', '2026-04-12 14:30:00+02');

-- Submission 2: monthly figures for March-May 2026 waiting for the approver (approve/reject demo).
INSERT INTO budget_submission (submission_id, unit_id, submitted_by, period_start, period_end, status, submitted_at, import_method) VALUES
    ('40000000000000000000000000000002', '20000000000000000000000000000001', '30000000000000000000000000000001',
     '2026-03-01', '2026-05-31', 'submitted', '2026-06-08 10:15:00+02', 'manual');

INSERT INTO financial_line_item (submission_id, category, period_start, amount, frequency)
SELECT '40000000000000000000000000000002', v.category, make_date(2026, 2 + m.n, 1), v.amounts[m.n], 'monthly'
FROM (VALUES
    ('revenue',        ARRAY[468000, 452000, 475000]::numeric[]),
    ('cost_of_sales',  ARRAY[281000, 270000, 286000]::numeric[]),
    ('employee_costs', ARRAY[ 86000,  86000,  86000]::numeric[]),
    ('electricity',    ARRAY[ 10900,  11400,  12600]::numeric[])
) AS v(category, amounts)
CROSS JOIN generate_series(1, 3) AS m(n);

-- Submission 3: a draft the submitter is still editing (no submitted_at until it is submitted).
INSERT INTO budget_submission (submission_id, unit_id, submitted_by, period_start, period_end, status, import_method) VALUES
    ('40000000000000000000000000000003', '20000000000000000000000000000001', '30000000000000000000000000000001',
     '2026-06-01', '2026-06-30', 'draft', 'manual');

INSERT INTO financial_line_item (submission_id, category, period_start, amount, frequency) VALUES
    ('40000000000000000000000000000003', 'revenue',       '2026-06-01', 461000, 'monthly'),
    ('40000000000000000000000000000003', 'cost_of_sales', '2026-06-01', 277000, 'monthly');

-- KPI targets for FY2027 (starts 2026-03-01), for the decision maker's targets screen.
INSERT INTO kpi_target (unit_id, metric_name, target_value, measurement_unit, target_period) VALUES
    ('20000000000000000000000000000001', 'Revenue',             5800000, 'ZAR',     '2026-03-01'),
    ('20000000000000000000000000000001', 'Gross profit margin',      40, 'percent', '2026-03-01'),
    ('20000000000000000000000000000001', 'Electricity cost',     125000, 'ZAR',     '2026-03-01');

COMMIT;
