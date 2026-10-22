# SME Budget Forecasting System

ITMDA3-34 group project (Eduvos, Group 1, 2026). A lightweight budget forecasting app for South African SMEs.

A submitter enters past income statements. The system forecasts each category with Moving Average and Exponential Smoothing, shows warnings from external economic data (inflation, fuel, electricity), and lets managers approve budgets and set KPI targets. It supports budgeting decisions. It is **not** an accounting system. No ARIMA or machine learning. An Excel importer is not part of this deliverable.

**Roles.** The Submitter uploads data, the Approver approves or rejects it, and the Decision Maker views forecasts, targets and KPIs.

## Marking demo

From `csharp-ingestion/`, with Docker Desktop running:

```powershell
docker compose up -d --build
```

That starts the database, alerts, forecasts and the Java API. Do not also run `gradlew bootRun`; both want port 8080. Open `android-app/` in Android Studio and press Run. Password for every account is `Test@1234`.

| Role | Email | What to click |
|---|---|---|
| Submitter | `submitter@example.com` | Open a draft, edit a line, submit |
| Approver | `approver@example.com` | Approve or reject the waiting submission |
| Decision Maker | `decisionmaker@example.com` | Open Forecasts and alerts, tap **Run forecast**, then open Meeting report |

Fuel, electricity and inflation warnings are already in the sample data. Forecast figures on the report stay `n/a` until **Run forecast**.

## What D4 requires

This is a working Android app, not a set of designs:

1. **Screens.** All core screens work, use consistent styling and match our wireframes.
2. **Authentication.** Login and logout (sign-up or password reset optional), secure handling of passwords, and only logged-in users get in.
3. **Backend integration.** The app reads and writes the PostgreSQL database through an API.
4. **At least 2 complete CRUD modules.** Each one can create, read, update and delete, with its own screens, logic and backend.
5. **Navigation.** Screens follow a logical flow, and the back button works without crashing the app.
6. **UI feedback.** Every action shows success or error messages, input validation and a loading indicator.
7. **Code quality.** Clean structure, consistent style, comments on the important logic, and no unused code or files.

## Team and work

Each member does about 25% of the work. Each Java member owns one feature end to end: Android screens, Spring Boot endpoints and database reads and writes. That way every person's work shows in their own commits and can be marked. Liam answers database, C# and Java questions and reviews code, but does not write other members' features.

| Member | Owns for D4 | Brief sections |
|---|---|---|
| **Liam** | Database, sample data, C# external data service, Python forecasting service, and Docker Compose, including the Spring Boot API | 3, 7, integration |
| **Dean** | Login and app structure: login, logout and register screens, Spring Boot login endpoints (BCrypt passwords, token or session), role-based home menu, navigation and back stack, shared theme and styles | 1, 2, 5 |
| **Aiden** | CRUD module 1, budget submissions: create, view, edit and delete submissions and their line items; submit, approve and reject; screens and Spring Boot endpoints | 3, 4, 6 |
| **Boitu** | CRUD module 2, KPI targets and the Decision Maker view: create, view, edit and delete KPI targets; forecasts and alerts; a meeting report the decision maker can email from the phone | 3, 4, 6 |

Everyone handles UI feedback (messages, validation, loading) and comments in their own screens and code.

## Status

| Part | Folder | Technology | Owner | Status |
|---|---|---|---|---|
| Database (18 tables) | `database/` | PostgreSQL | Liam | Done |
| Sample test data | `database/init/03_seed.sql` | SQL | Liam | Done |
| External data and warning rules | `csharp-ingestion/` | C# .NET 10 | Liam | Done |
| Forecasting (MA and ES) | `python-forecast/` | Python FastAPI | Liam | Done (runs on sample data) |
| Login, roles, navigation | `android-app/`, `java-api/` | Android (Java), Spring Boot | Dean | Done |
| Budget submissions CRUD and approval | `android-app/`, `java-api/` | Android (Java), Spring Boot | Aiden | Done |
| KPI targets CRUD, forecasts and alerts view | `android-app/`, `java-api/` | Android (Java), Spring Boot | Boitu | Done |
| Meeting report (grouped view, share by the phone's mail app) | `android-app/`, `java-api/` `targets/` | Android (Java), Spring Boot | Boitu | Done |
| Spring Boot API in Docker Compose | `csharp-ingestion/docker-compose.yml`, `java-api/` | Docker | Liam | Done. Published on port 8080 |

## Repo structure

```text
database/                 shared PostgreSQL schema and sample data (the only place tables are created)
csharp-ingestion/         C# service: World Bank and OECD data, warning rules, docker-compose.yml
python-forecast/          Python service: Moving Average and Exponential Smoothing
android-app/              Android Studio project (Java), created by Dean
java-api/                 Spring Boot API: login, submissions, KPI targets, created by Dean
team-submissions/         personal notes, experiments and individual write-ups per member
```

Inside `android-app/` and `java-api/`, each member's feature is its own package: `auth/` and `home/` (Dean), `submissions/` (Aiden) and `targets/` (Boitu). Tables are created only by `database/init/`.

## Run everything

You need Docker Desktop running. From `csharp-ingestion/`:

```powershell
Copy-Item .env.example .env      # first time only
docker compose up -d --build
```

| Service | URL from your PC | URL from the Android emulator |
|---|---|---|
| Java API (the app) | http://localhost:8080 | `http://10.0.2.2:8080` |
| C# external data API | http://localhost:5080/swagger | Not called by the app |
| Python forecasting API | http://localhost:5090/docs | Not called by the app |
| PostgreSQL | localhost:5432, database `budget_prototype`, user `budget` | Not reachable |

The ports are bound to `127.0.0.1`, so only this PC and the Android emulator can reach them. The emulator uses `10.0.2.2` instead of `localhost`. The app already allows that local address.

`docker compose stop` stops the containers and keeps the data. `docker compose down -v` **deletes** the database, and Docker rebuilds it from `database/init/` on the next start. Run it once after pulling `03_seed.sql` to load the sample data.

## Sample logins (fictional test data)

`database/init/03_seed.sql` creates a fictional business ("Sample Hardware Store") with 5 years of approved income statement history, one submission waiting for approval, one draft, three KPI targets and three open economic warnings. All figures are made up.

| Role | Email | Password |
|---|---|---|
| Submitter | `submitter@example.com` | `Test@1234` |
| Approver | `approver@example.com` | `Test@1234` |
| Decision Maker | `decisionmaker@example.com` | `Test@1234` |

The database stores passwords as BCrypt hashes (`$2a$10$...`), which Spring Security's `BCryptPasswordEncoder` checks directly. Business unit ID for API tests: `20000000000000000000000000000001`.

## Secrets and `.env` files

`.env` files hold passwords. Never commit them, not even to this private repo. Git history keeps them forever, and D4 marks secure credential handling.

- You do not need anyone else's `.env`. Each member's Docker database runs only on their own PC. Copy the template once (`Copy-Item .env.example .env` in `csharp-ingestion/`) and use any password you like.
- `.env.example` is the committed template, with setting names and placeholder values only.
- No API keys are needed. The World Bank and OECD APIs are free and need no key.
- The API reads `POSTGRES_PASSWORD` from the environment. It is not hard-coded. Login uses a random session token stored only in memory, not a JWT secret in the repo.

## Rules

- Java never creates or changes tables (`spring.jpa.hibernate.ddl-auto=validate`). Schema changes belong in `database/init/01_schema.sql`. Names and allowed values are in `database/README.md`.
- Role values are exactly `submitter`, `approver` and `decision_maker`.
- Forecasting uses only Moving Average and Exponential Smoothing.
- The sample logins above are fictional test data. Real business data and `.env` files are not in this repo.
