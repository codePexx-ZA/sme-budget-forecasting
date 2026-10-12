# Budget system external data ingestion service (C#)

Fetches South African economic indicators from the World Bank and OECD APIs, stores them in PostgreSQL, and raises warning alerts when a value crosses a rule threshold. Also holds `docker-compose.yml` for the whole system.

## What this does

1. Fetches four South African indicators as annual % change for the last 6 years (set by `IndicatorCatalog.HistoryYears`). Later, each business will use only the years inside its own income statement history.

   | Code | Indicator | API |
   |---|---|---|
   | `cpi_headline` | Headline consumer inflation | World Bank `FP.CPI.TOTL.ZG` |
   | `cpi_core` | Core inflation (CPI excl. food and energy; replaces CPIX) | OECD `_TXCP01_NRG` |
   | `electricity_price` | Electricity price change | OECD `CP045` |
   | `fuel_price` | Fuel price change | OECD `CP0722` |

   Neither API needs a key. Where each one is fetched from is in `Services/IndicatorCatalog.cs`.
2. Validates country and year (wrong ones are rejected) and flags extreme values for review.
3. Upserts rows into `data_source` and `external_indicator`.
4. Runs one rule per indicator: a validated value above the rule's `threshold_value` creates a `rule_alert` naming the income statement categories to review. Re-running the ingest does not create duplicate alerts.
5. Read endpoints return indicators, alerts, the indicator catalog and the categories.

## Prerequisites

- Docker Desktop (running)
- .NET SDK 10, only if you run the API outside Docker with `dotnet run`

## 0. One-time setup (passwords are not in the code)

From the `csharp-ingestion` folder, create `.env` for Docker Compose:

```powershell
Copy-Item .env.example .env
```

For `dotnet run`, store the full connection string in .NET user secrets (kept outside the project folder). Use the same password as `POSTGRES_PASSWORD` in your `.env`:

```powershell
cd BudgetIngestion
dotnet user-secrets set "ConnectionStrings:DefaultConnection" "Host=localhost;Port=5432;Database=budget_prototype;Username=budget;Password=<your POSTGRES_PASSWORD>"
cd ..
```

## 1. Start PostgreSQL

From the `csharp-ingestion` folder:

```powershell
docker compose up -d postgres
```

Or run the database **and** the C# API in containers (Swagger on the same port 5080, so do not also `dotnet run`):

```powershell
docker compose up -d --build
```

Check it is healthy:

```powershell
docker compose ps
```

## 2. Run the C# API locally

```powershell
cd BudgetIngestion
dotnet run
```

Open Swagger: http://localhost:5080/swagger

## 3. Trigger an ingest

In Swagger, call:

`POST /api/ingestion/run` (all four indicators) or `POST /api/ingestion/{code}` (one indicator, e.g. `fuel_price`).

Or from PowerShell:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:5080/api/ingestion/run
```

Then:

| Endpoint | Returns |
|---|---|
| `GET /api/indicators?code=fuel_price&take=10` | Saved indicator values (`code` optional) |
| `GET /api/indicators/alerts` | Warning alerts |
| `GET /api/indicators/catalog` | The four indicators, their sources, thresholds and linked categories |
| `GET /api/indicators/categories` | The 13 income statement categories |

## Connection string

`appsettings.json` holds the connection string **without** the password. The full string comes from user secrets (`dotnet run`) or the `ConnectionStrings__DefaultConnection` environment variable (Docker Compose, using `POSTGRES_PASSWORD` from `.env`).

## Tables this service uses

This service does **not** create tables or insert reference data; both come from the scripts in `../database/init/` (see `../database/README.md`).

It writes `data_source`, `external_indicator` and `rule_alert`, and reads `rule_definition`, `indicator_type`, `indicator_category_link` and `line_category`.

## Next steps

- The Android app reads alerts and indicators from `/api/indicators/alerts` and `/api/indicators/catalog`.
- Income statement Excel importer (template + label mapping), after D4.
