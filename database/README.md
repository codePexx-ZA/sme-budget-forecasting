# Shared PostgreSQL schema

This folder owns **all tables**: the 14 from the D3 ERD plus 3 reference tables and `import_mapping` (18 total). No service creates or changes tables itself:

- The C# ingestion service (EF Core) and the Java API (JPA/Hibernate) only **read and write** tables that already exist.
- The Python forecasting service does the same.

## How it runs

Docker's `postgres` image runs every `.sql` file in `init/` in **alphabetical order** the first time the database volume is created.

```text
init/
  01_schema.sql          -- CREATE TABLE for all 18 tables (parents before children)
  02_reference_data.sql  -- categories, external indicator types, indicator -> category links, warning rules
  03_seed.sql            -- FICTIONAL test data: sample business, one user per role, income statement history, KPI targets
```

`03_seed.sql` uses fixed IDs (business `1000…0001`, unit `2000…0001`, users `3000…0001`–`0003`, submissions `4000…0001`–`0003`) so screens and API tests can refer to the same records. It creates the `pgcrypto` extension to store BCrypt password hashes. All figures are made up; replace them with real participant data only through the app, never in this file.

The scripts only run on an **empty** volume. After changing a script, reset the database:

```powershell
cd ..\csharp-ingestion
docker compose down -v
docker compose up -d
```

`down -v` deletes the database volume and all data in it. Re-run the ingest (`POST /api/ingestion/run`) afterwards to refill the external indicators.

## Conventions in `01_schema.sql`

- **IDs:** every primary key is `VARCHAR(32)`, a UUID without dashes (C# `Guid.ToString("N")`). Services may supply their own ID; if they don't, Postgres generates one. Reference tables use readable codes instead (`category_code`, `indicator_code`).
- **Money:** `NUMERIC(18,2)`. **Indicator values / rule thresholds:** `NUMERIC(18,6)`.
- **Timestamps:** `TIMESTAMPTZ` (UTC). **Calendar periods:** `DATE` (first day of the period), except `external_indicator.relevant_period`, which stays text (`"2024"`) for the C# service.
- **Forecast parameters:** `forecast_run.parameters` is `JSONB`, e.g. `{"window_size": 3}` or `{"alpha": 0.5}`.

## Allowed status values (enforced by `CHECK` constraints)

Every service must use exactly these lowercase strings, or the insert is rejected.

| Table.column | Allowed values |
|---|---|
| `business.year_end_month` | `1`–`12` (last month of the financial year; default `2` = February) |
| `business_unit.unit_type` | `company` (default; the prototype's one "Whole company" unit), `store`, `branch`, `department` |
| `app_user.role` | `submitter`, `approver`, `decision_maker` |
| `app_user.active_status`, `rule_definition.active_status` | `active`, `inactive` |
| `budget_submission.status` | `draft`, `submitted`, `approved`, `rejected`, `superseded` |
| `budget_submission.import_method` | `template`, `mapped_import`, `manual` |
| `budget_submission.source_scale` | `1`, `1000` (R'000), `1000000` (R million) |
| `financial_line_item.frequency` | `monthly`, `quarterly`, `annual` (default) |
| `import_mapping.split_percent` | above 0, up to 100 (rows for one label must add up to 100 — checked by the importer) |
| `approval_action.decision` | `approved`, `rejected` (a rejection must have `comments`) |
| `data_source.source_type` | `api`, `spreadsheet`, `dataset`, `manual` |
| `data_source.trusted_status` | `trusted`, `untrusted` |
| `external_indicator.validation_status` | `pending`, `validated`, `flagged`, `rejected` |
| `forecast_run.forecasting_method` | `moving_average`, `exponential_smoothing` |
| `rule_alert.severity` | `info`, `warning`, `critical` |
| `rule_alert.resolution_status` | `open`, `acknowledged`, `resolved` |

`external_indicator.indicator_code` and `rule_definition.indicator_code` must exist in `indicator_type` (foreign key). `financial_line_item.category`, `forecast_value.category` and `import_mapping.category_code` must exist in `line_category`.

## Income statement uploads

**History length:** 3–5 years of annual statements, or at least 24 months of monthly reports. Shorter histories still forecast, with a warning.

- **One upload = one `budget_submission`.** `period_start` / `period_end` span every period in the file (e.g. 5 financial years).
- **One `financial_line_item` per category per period.** `period_start` is the first day of the month, quarter or financial year (for a February year-end, FY2025 starts `2024-03-01`). Amounts are stored in rands after applying `source_scale`.
- **Re-uploads (versioning):** a corrected upload sets `replaces_submission_id` to the version it replaces. When the new one is approved, the old one's status becomes `superseded`; it is kept for history, never deleted. Forecasts read only `approved` submissions.

## Income statement categories (`line_category`)

Every imported income statement row is mapped to one of these fixed categories (based on IFRS for SMEs, expenses classified by nature). Forecasting runs per category. Inserted by `02_reference_data.sql`.

| # | `category_code` | Category | Team-managed | Typical source labels |
|---|---|---|---|---|
| 1 | `revenue` | Revenue | **Yes** | Sales, Turnover, Income, Income from services |
| 2 | `other_income` | Other income | | Interest received, Sundry income |
| 3 | `cost_of_sales` | Cost of sales | | COGS, Purchases, Direct costs |
| 4 | `employee_costs` | Employee costs | **Yes** | Salaries, Wages, Salaries & wages, UIF, SDL |
| 5 | `occupancy` | Occupancy | | Rent, Rates, Lease, Rent & rates |
| 6 | `electricity` | Electricity | | Electricity, Eskom, Prepaid electricity |
| 7 | `other_utilities` | Water, telecoms and other utilities | | Water, Telephone, Internet |
| 8 | `transport_fuel` | Transport and fuel | | Fuel, Petrol, Diesel, Delivery costs, Motor vehicle expenses |
| 9 | `marketing` | Marketing | | Advertising, Promotions |
| 10 | `other_operating` | Other operating expenses | | Admin, Bank charges, Repairs |
| 11 | `depreciation` | Depreciation and amortisation | | Depreciation |
| 12 | `finance_costs` | Finance costs | | Interest paid |
| 13 | `income_tax` | Income tax | | Tax, SARS |

**Team-managed** categories (sales and salaries) are set by the business itself, e.g. salaries at inflation + 1%. No external indicator applies to them.

Subtotals (gross profit, operating profit, net profit) are **not** categories; calculate them from the categories when displaying.

**Planned Excel importer (after D4):** rows will be matched to categories by label (not row position), and the user's choices saved per business in `import_mapping`. A combined line such as "Water & electricity" gets one row per category with a `split_percent` (e.g. 70% `electricity` + 30% `other_utilities`) that must add up to 100. Not used in D4.

## External indicators (`indicator_type`, `indicator_category_link`, `rule_definition`)

The C# service fetches these automatically (annual % change, South Africa). Each indicator is linked to the income statement categories it affects, and has one warning rule.

| `indicator_code` | Indicator | API source | Linked categories | Rule threshold |
|---|---|---|---|---|
| `cpi_headline` | Consumer inflation (headline CPI) | World Bank `FP.CPI.TOTL.ZG` | Cost of sales | 6% |
| `cpi_core` | Core inflation (replaces CPIX, discontinued 2009) | OECD `_TXCP01_NRG` | Occupancy; Water, telecoms and other utilities; Marketing; Other operating expenses | 6% |
| `electricity_price` | Electricity price change | OECD `CP045` | Electricity | 10% |
| `fuel_price` | Fuel price change | OECD `CP0722` | Transport and fuel | 10% |

Thresholds are starting values to confirm with the business. Change them in `02_reference_data.sql` (then reset) or directly in `rule_definition.threshold_value`. To add an indicator: insert it into `indicator_type`, link it to categories, add a rule, and add its API series to `IndicatorCatalog.cs`.

## Differences from the D3 ERD

- `app_user.password_hash` added (nullable) for the Java login.
- `rule_definition.threshold_value` and `indicator_code` added so rules are configured in the database, not in code.
- `external_indicator.indicator_code` added (stable code; unique per source and period).
- New reference tables: `line_category`, `indicator_type`, `indicator_category_link`.
- New table `import_mapping` (remembered label → category choices per business, with % splits).
- `business.year_end_month` added; `business_unit.unit_type` gains `company`.
- `budget_submission`: `replaces_submission_id`, `import_method`, `source_file_name`, `source_scale` added; status gains `superseded`.
- `financial_line_item.period_start` added (one row per category per period); `category` is now a foreign key to `line_category`, as is `forecast_value.category`.
- `forecast_run.historical_period` split into `history_start` / `history_end` (`DATE`); `submission_id` is nullable because a forecast usually draws on several approved submissions.
- `forecast_value.forecast_period` and `kpi_target.target_period` are `DATE`.

## Tables the C# service maps

The C# service maps `data_source`, `external_indicator`, `rule_definition`, `rule_alert`, `line_category`, `indicator_type` and `indicator_category_link` in `csharp-ingestion/BudgetIngestion/Data/IngestionDbContext.cs`. If you change any of these tables, update that file to match. Timestamps must stay `TIMESTAMPTZ`, because the C# service writes UTC times.
