-- Creates the shared PostgreSQL schema (tables, constraints and indexes) for the budget prediction system.

BEGIN;

-- IDs are 32-character hex strings (a UUID without dashes). Services may supply their own; otherwise the default generates one.
-- Services only read and write these tables; they never create or alter them.

CREATE TABLE line_category (
    category_code VARCHAR(50)  PRIMARY KEY,
    display_name  VARCHAR(100) NOT NULL UNIQUE,
    sort_order    SMALLINT     NOT NULL,
    team_managed  BOOLEAN      NOT NULL DEFAULT false
);

CREATE TABLE indicator_type (
    indicator_code   VARCHAR(50)  PRIMARY KEY,
    display_name     VARCHAR(200) NOT NULL,
    description      VARCHAR(1000),
    measurement_unit VARCHAR(50)  NOT NULL DEFAULT 'percent',
    official_source  VARCHAR(500) NOT NULL
);

CREATE TABLE indicator_category_link (
    indicator_code VARCHAR(50) NOT NULL REFERENCES indicator_type (indicator_code) ON DELETE CASCADE,
    category_code  VARCHAR(50) NOT NULL REFERENCES line_category (category_code) ON DELETE CASCADE,
    PRIMARY KEY (indicator_code, category_code)
);

-- year_end_month: last month of the financial year (2 = February), used to turn "FY2024" into dates.
CREATE TABLE business (
    business_id    VARCHAR(32)  PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    legal_name     VARCHAR(200) NOT NULL,
    industry       VARCHAR(100),
    country        CHAR(3)      NOT NULL DEFAULT 'ZAF',
    year_end_month SMALLINT     NOT NULL DEFAULT 2 CHECK (year_end_month BETWEEN 1 AND 12),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE business_unit (
    unit_id       VARCHAR(32)  PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    business_id   VARCHAR(32)  NOT NULL REFERENCES business (business_id) ON DELETE RESTRICT,
    unit_name     VARCHAR(200) NOT NULL,
    unit_type     VARCHAR(20)  NOT NULL DEFAULT 'company'
                  CHECK (unit_type IN ('company', 'store', 'branch', 'department')),
    currency      CHAR(3)      NOT NULL DEFAULT 'ZAR',
    UNIQUE (business_id, unit_name)
);

CREATE TABLE app_user (
    user_id       VARCHAR(32)  PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    unit_id       VARCHAR(32)  NOT NULL REFERENCES business_unit (unit_id) ON DELETE RESTRICT,
    full_name     VARCHAR(200) NOT NULL,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255),
    role          VARCHAR(20)  NOT NULL
                  CHECK (role IN ('submitter', 'approver', 'decision_maker')),
    active_status VARCHAR(20)  NOT NULL DEFAULT 'active'
                  CHECK (active_status IN ('active', 'inactive'))
);

-- One row per upload. A corrected re-upload points at the version it replaces; when the new
-- version is approved, the old one becomes 'superseded' and is kept for history.
-- source_scale: multiplier confirmed on import (1000 for R'000); amounts are stored in rands.
CREATE TABLE budget_submission (
    submission_id          VARCHAR(32)  PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    unit_id                VARCHAR(32)  NOT NULL REFERENCES business_unit (unit_id) ON DELETE RESTRICT,
    submitted_by           VARCHAR(32)  NOT NULL REFERENCES app_user (user_id) ON DELETE RESTRICT,
    period_start           DATE         NOT NULL,
    period_end             DATE         NOT NULL,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'draft'
                           CHECK (status IN ('draft', 'submitted', 'approved', 'rejected', 'superseded')),
    submitted_at           TIMESTAMPTZ,
    replaces_submission_id VARCHAR(32)  REFERENCES budget_submission (submission_id) ON DELETE RESTRICT,
    import_method          VARCHAR(20)  NOT NULL DEFAULT 'template'
                           CHECK (import_method IN ('template', 'mapped_import', 'manual')),
    source_file_name       VARCHAR(255),
    source_scale           INTEGER      NOT NULL DEFAULT 1 CHECK (source_scale IN (1, 1000, 1000000)),
    CHECK (period_end >= period_start),
    CHECK (status = 'draft' OR submitted_at IS NOT NULL),
    CHECK (replaces_submission_id IS NULL OR replaces_submission_id <> submission_id)
);

-- One amount per category per period. period_start is the first day of the month, quarter or financial year.
CREATE TABLE financial_line_item (
    line_item_id  VARCHAR(32)   PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    submission_id VARCHAR(32)   NOT NULL REFERENCES budget_submission (submission_id) ON DELETE CASCADE,
    category      VARCHAR(50)   NOT NULL REFERENCES line_category (category_code) ON DELETE RESTRICT,
    period_start  DATE          NOT NULL,
    amount        NUMERIC(18,2) NOT NULL,
    frequency     VARCHAR(20)   NOT NULL DEFAULT 'annual'
                  CHECK (frequency IN ('monthly', 'quarterly', 'annual')),
    UNIQUE (submission_id, category, period_start)
);

CREATE TABLE approval_action (
    approval_id   VARCHAR(32)   PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    submission_id VARCHAR(32)   NOT NULL REFERENCES budget_submission (submission_id) ON DELETE CASCADE,
    acted_by      VARCHAR(32)   NOT NULL REFERENCES app_user (user_id) ON DELETE RESTRICT,
    decision      VARCHAR(20)   NOT NULL CHECK (decision IN ('approved', 'rejected')),
    comments      VARCHAR(2000),
    acted_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (decision = 'approved' OR comments IS NOT NULL)
);

-- Remembered label -> category choices per business. A combined line such as "Water & electricity"
-- has one row per category, and the importer checks that split_percent adds up to 100.
CREATE TABLE import_mapping (
    mapping_id    VARCHAR(32)  PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    business_id   VARCHAR(32)  NOT NULL REFERENCES business (business_id) ON DELETE CASCADE,
    source_label  VARCHAR(200) NOT NULL,
    label_key     VARCHAR(200) NOT NULL,
    category_code VARCHAR(50)  NOT NULL REFERENCES line_category (category_code) ON DELETE RESTRICT,
    split_percent NUMERIC(5,2) NOT NULL DEFAULT 100 CHECK (split_percent > 0 AND split_percent <= 100),
    created_by    VARCHAR(32)  REFERENCES app_user (user_id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (business_id, label_key, category_code)
);

CREATE TABLE data_source (
    source_id         VARCHAR(32)  PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    source_name       VARCHAR(200) NOT NULL,
    source_identifier VARCHAR(500) NOT NULL UNIQUE,
    source_type       VARCHAR(50)  NOT NULL DEFAULT 'api'
                      CHECK (source_type IN ('api', 'spreadsheet', 'dataset', 'manual')),
    trusted_status    VARCHAR(50)  NOT NULL DEFAULT 'trusted'
                      CHECK (trusted_status IN ('trusted', 'untrusted'))
);

CREATE TABLE external_indicator (
    indicator_id      VARCHAR(32)   PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    source_id         VARCHAR(32)   NOT NULL REFERENCES data_source (source_id) ON DELETE CASCADE,
    indicator_code    VARCHAR(50)   NOT NULL REFERENCES indicator_type (indicator_code) ON DELETE RESTRICT,
    indicator_name    VARCHAR(200)  NOT NULL,
    indicator_value   NUMERIC(18,6) NOT NULL,
    measurement_unit  VARCHAR(50)   NOT NULL,
    relevant_period   VARCHAR(20)   NOT NULL,
    validation_status VARCHAR(50)   NOT NULL DEFAULT 'pending'
                      CHECK (validation_status IN ('pending', 'validated', 'flagged', 'rejected')),
    collected_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (source_id, indicator_code, relevant_period)
);

CREATE INDEX ix_external_indicator_code_period ON external_indicator (indicator_code, relevant_period);

-- Written by the Python forecasting service; adjusted_amount is reserved for later rule-based adjustments.
CREATE TABLE forecast_run (
    run_id             VARCHAR(32) PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    unit_id            VARCHAR(32) NOT NULL REFERENCES business_unit (unit_id) ON DELETE RESTRICT,
    submission_id      VARCHAR(32) REFERENCES budget_submission (submission_id) ON DELETE RESTRICT,
    forecasting_method VARCHAR(30) NOT NULL
                       CHECK (forecasting_method IN ('moving_average', 'exponential_smoothing')),
    history_start      DATE        NOT NULL,
    history_end        DATE        NOT NULL,
    parameters         JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (history_end >= history_start)
);

CREATE TABLE forecast_value (
    value_id        VARCHAR(32)   PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    run_id          VARCHAR(32)   NOT NULL REFERENCES forecast_run (run_id) ON DELETE CASCADE,
    category        VARCHAR(50)   NOT NULL REFERENCES line_category (category_code) ON DELETE RESTRICT,
    forecast_period DATE          NOT NULL,
    baseline_amount NUMERIC(18,2) NOT NULL,
    adjusted_amount NUMERIC(18,2),
    UNIQUE (run_id, category, forecast_period)
);

CREATE TABLE forecast_input (
    input_id              VARCHAR(32)   PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    run_id                VARCHAR(32)   NOT NULL REFERENCES forecast_run (run_id) ON DELETE CASCADE,
    indicator_id          VARCHAR(32)   NOT NULL REFERENCES external_indicator (indicator_id) ON DELETE RESTRICT,
    influence_description VARCHAR(1000),
    UNIQUE (run_id, indicator_id)
);

CREATE TABLE kpi_target (
    target_id        VARCHAR(32)   PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    unit_id          VARCHAR(32)   NOT NULL REFERENCES business_unit (unit_id) ON DELETE RESTRICT,
    metric_name      VARCHAR(100)  NOT NULL,
    target_value     NUMERIC(18,2) NOT NULL,
    measurement_unit VARCHAR(50)   NOT NULL,
    target_period    DATE          NOT NULL,
    UNIQUE (unit_id, metric_name, target_period)
);

CREATE TABLE rule_definition (
    rule_id         VARCHAR(32)   PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    rule_name       VARCHAR(200)  NOT NULL UNIQUE,
    condition       VARCHAR(500)  NOT NULL,
    action          VARCHAR(500)  NOT NULL,
    indicator_code  VARCHAR(50)   REFERENCES indicator_type (indicator_code) ON DELETE RESTRICT,
    threshold_value NUMERIC(18,6),
    active_status   VARCHAR(50)   NOT NULL DEFAULT 'active'
                    CHECK (active_status IN ('active', 'inactive'))
);

CREATE TABLE rule_alert (
    alert_id          VARCHAR(32)   PRIMARY KEY DEFAULT replace(gen_random_uuid()::text, '-', ''),
    rule_id           VARCHAR(32)   NOT NULL REFERENCES rule_definition (rule_id) ON DELETE RESTRICT,
    unit_id           VARCHAR(32)   REFERENCES business_unit (unit_id) ON DELETE SET NULL,
    run_id            VARCHAR(32)   REFERENCES forecast_run (run_id) ON DELETE SET NULL,
    severity          VARCHAR(50)   NOT NULL DEFAULT 'info'
                      CHECK (severity IN ('info', 'warning', 'critical')),
    message           VARCHAR(1000) NOT NULL,
    resolution_status VARCHAR(50)   NOT NULL DEFAULT 'open'
                      CHECK (resolution_status IN ('open', 'acknowledged', 'resolved')),
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Postgres does not index foreign keys automatically, so they are indexed here.
CREATE INDEX ix_business_unit_business      ON business_unit (business_id);
CREATE INDEX ix_app_user_unit               ON app_user (unit_id);
CREATE INDEX ix_budget_submission_unit      ON budget_submission (unit_id, period_start);
CREATE INDEX ix_budget_submission_user      ON budget_submission (submitted_by);
CREATE INDEX ix_budget_submission_replaces  ON budget_submission (replaces_submission_id);
CREATE INDEX ix_financial_line_item_cat     ON financial_line_item (category, period_start);
CREATE INDEX ix_approval_action_submission  ON approval_action (submission_id);
CREATE INDEX ix_approval_action_user        ON approval_action (acted_by);
CREATE INDEX ix_import_mapping_category     ON import_mapping (category_code);
CREATE INDEX ix_import_mapping_user         ON import_mapping (created_by);
CREATE INDEX ix_forecast_run_unit           ON forecast_run (unit_id, created_at);
CREATE INDEX ix_forecast_run_submission     ON forecast_run (submission_id);
CREATE INDEX ix_forecast_value_category     ON forecast_value (category);
CREATE INDEX ix_forecast_input_indicator    ON forecast_input (indicator_id);
CREATE INDEX ix_indicator_category_link_cat ON indicator_category_link (category_code);
CREATE INDEX ix_rule_definition_indicator   ON rule_definition (indicator_code);
CREATE INDEX ix_rule_alert_rule             ON rule_alert (rule_id);
CREATE INDEX ix_rule_alert_unit             ON rule_alert (unit_id);
CREATE INDEX ix_rule_alert_run              ON rule_alert (run_id);
CREATE INDEX ix_rule_alert_created          ON rule_alert (created_at DESC);

COMMIT;
