# SME Budget Forecasting System

ITMDA3-34 group project (Eduvos, Group 1, 2026) — a lightweight budget forecasting app for **South African SMEs**.

Businesses upload past **income statements**. The system forecasts each income statement category with **Moving Average** and **Exponential Smoothing**, shows warnings from external economic data (inflation, fuel, electricity), and lets managers approve budgets, targets and KPIs. It supports budgeting decisions; it is **not** an accounting system. No ARIMA or machine learning.

**Roles:** Submitter (uploads data) → Approver (approves or rejects) → Decision Maker (views forecasts, targets, KPIs).

## Deadlines

| Deliverable | Due |
|---|---|
| D4 Implementation (50 marks) | **Fri 23 Oct 2026, 23:59** |
| D5 | Fri 30 Oct 2026, 23:59 |
| D6 | Fri 13 Nov 2026, 23:59 |

**D4 team milestones**

| Date | Milestone |
|---|---|
| Tue 13 Oct | Dean pushes the **one shared** Android Studio project (`android-app/`) and Spring Boot project (`java-api/`) with login + navigation shell. Aiden and Boitu build their modules inside these — not in separate projects. |
| Wed 14 – Thu 15 Oct | Quick check by Liam: app starts and logs in with `submitter@example.com` / `Test@1234`. |
| Sun 18 Oct | All features pushed and handed over to Liam (keep pushing weekly before this). |
| 18 – 23 Oct | Liam integrates, adds Spring Boot to Docker Compose, fixes issues, final review. |
| Fri 23 Oct, 23:59 | D4 submitted. |

**D4 needs a working Android mobile app**, not just designs:

1. **Screens** — all core screens, fully functional, consistent styling, matching our wireframes.
2. **Authentication** — login/logout (sign-up or password reset optional), secure credential handling, only logged-in users get in.
3. **Backend integration** — app reads and writes the PostgreSQL database through an API.
4. **At least 2 complete CRUD modules** — create, read, update, delete, each with screens, logic and backend.
5. **Navigation** — logical screen flow; the back button must work and never crash the app.
6. **UI feedback** — success/error messages, input validation, loading indicators on every action.
7. **Code quality** — clean structure, consistent style, **comments on the important logic**, no unused code or files.

## Team and work

Everyone does a fair share (about 25% each). Each Java member owns one feature **end to end** — Android screens + Spring Boot endpoints + database reads/writes — so every person's work is visible and gradable in their own commits. Liam answers database and C#/Java questions and reviews, but does not write, other members' features.

| Member | Owns for D4 | Brief sections |
|---|---|---|
| **Liam** | Database, sample data, C# external data service, Python forecasting service, Docker (done). Next: Spring Boot in Docker Compose, connecting the app to the forecast and alerts APIs, pull request reviews, final build | 3, 7, integration |
| **Dean** | **Login and app structure** — login/logout (and register) screens, Spring Boot login endpoints (BCrypt passwords, token or session), role-based home menu, navigation and back-stack, shared theme/styles | 1, 2, 5 |
| **Aiden** | **CRUD module 1: Budget submissions** — create, view, edit and delete submissions and their line items; submit, approve and reject; screens + Spring Boot endpoints | 3, 4, 6 |
| **Boitu** | **CRUD module 2: KPI targets + Decision Maker view** — create, view, edit and delete KPI targets; screen showing forecasts (Python API) and alerts (C# API) | 3, 4, 6 |

Everyone handles UI feedback (messages, validation, loading) and comments in their own screens and code.

## Status

| Part | Folder | Technology | Owner | Status |
|---|---|---|---|---|
| Database (18 tables) | `database/` | PostgreSQL | Liam | Done |
| Sample test data | `database/init/03_seed.sql` | SQL | Liam | Done |
| External data + warning rules | `csharp-ingestion/` | C# .NET 10 | Liam | Done |
| Forecasting (MA + ES) | `python-forecast/` | Python FastAPI | Liam | Done (runs on sample data) |
| Login, roles, navigation | `android-app/`, `java-api/` | Android (Java), Spring Boot | Dean | To do |
| Budget submissions CRUD + approval | `android-app/`, `java-api/` | Android (Java), Spring Boot | Aiden | To do |
| KPI targets CRUD + forecasts/alerts view | `android-app/`, `java-api/` | Android (Java), Spring Boot | Boitu | To do |
| Spring Boot in Docker Compose | `csharp-ingestion/docker-compose.yml` | Docker | Liam | When `java-api/` exists |
| Excel income statement importer | `csharp-ingestion/` | C# | Liam | After D4 (not required) |

## Repo structure

```text
database/                 shared PostgreSQL schema + sample data — the only place tables are created
csharp-ingestion/         C# service: World Bank / OECD data, warning rules (+ docker-compose.yml)
python-forecast/          Python service: Moving Average + Exponential Smoothing
android-app/              Android Studio project (Java) — created by Dean
java-api/                 Spring Boot API: login, submissions, KPI targets — created by Dean
team-submissions/         personal notes, experiments and individual write-ups per member
```

Inside `android-app/` and `java-api/`, each member works in **their own feature package**, e.g. `auth/` (Dean), `submissions/` (Aiden), `targets/` (Boitu). Shared files (navigation, theme, security config) are owned by Dean; ask before changing them. Do not edit `database/`, `csharp-ingestion/` or `python-forecast/` — ask Liam if something there needs to change.

## Run everything

Needs **Docker Desktop** (running). From `csharp-ingestion/`:

```powershell
Copy-Item .env.example .env      # first time only
docker compose up -d --build
```

| Service | URL from your PC | URL from the Android emulator |
|---|---|---|
| C# external data API | http://localhost:5080/swagger | `http://10.0.2.2:5080` |
| Python forecasting API | http://localhost:5090/docs | `http://10.0.2.2:5090` |
| PostgreSQL | localhost:5432, database `budget_prototype`, user `budget` | — |

The emulator cannot reach `localhost` on your PC; use `10.0.2.2` instead. Android also blocks plain `http://` by default, so for local development add `android:usesCleartextTraffic="true"` to the `<application>` tag in `AndroidManifest.xml`.

Stop with `docker compose stop` (keeps data). `docker compose down -v` **deletes** the database and rebuilds it from `database/init/` on next start — do this once after pulling `03_seed.sql` to load the sample data.

## Sample logins (fictional test data)

`database/init/03_seed.sql` creates a fictional business ("Sample Hardware Store") with 5 years of approved income statement history, one submission waiting for approval, one draft and three KPI targets. **All figures are made up** and only for testing until real participant data is available.

| Role | Email | Password |
|---|---|---|
| Submitter | `submitter@example.com` | `Test@1234` |
| Approver | `approver@example.com` | `Test@1234` |
| Decision Maker | `decisionmaker@example.com` | `Test@1234` |

Passwords are stored as BCrypt hashes (`$2a$10$...`), which Spring Security's `BCryptPasswordEncoder` checks directly. Business unit ID for API tests: `20000000000000000000000000000001`.

## Secrets and `.env` files

`.env` files hold passwords and are **never committed** — not even to this private repo (git history keeps them forever, and D4 marks secure credential handling).

- **You do not need anyone else's `.env`.** Each member's Docker database runs only on their own PC. Copy the template once (`Copy-Item .env.example .env` in `csharp-ingestion/`) and use any password you like.
- **`.env.example`** is the committed template: setting names with placeholder values only.
- **No API keys are needed** — the World Bank and OECD APIs are free and keyless.
- **Spring Boot (`java-api/`):** read secrets from environment variables, never hard-code them, e.g. `spring.datasource.password=${POSTGRES_PASSWORD}` and `jwt.secret=${JWT_SECRET}`. Add placeholders such as `JWT_SECRET=change-me` to `.env.example`.
- **If a real secret must be shared** (e.g. a cloud database for the demo), send it privately (direct message or password manager), never in the repo, and change it after marking.

## How we work on GitHub

1. Clone once: `git clone https://github.com/codePexx-ZA/sme-budget-forecasting.git`
2. Before starting work each time: `git pull`.
3. Work on **your own branch**: `git checkout -b dean-login` (or `aiden-submissions`, `boitu-targets`).
4. Commit small, clear changes: `git commit -m "Add login screen"`, then `git push`.
5. Open a **pull request** into `main` when a piece works; Liam reviews and merges.
6. Push **at least once a week** — do not wait until the deadline.

## Rules

- **Do not create or change tables in code** (Java: `spring.jpa.hibernate.ddl-auto=validate`). Ask Liam to change `database/init/01_schema.sql`. Table and column names, and allowed status values, are in `database/README.md`.
- Use exactly these role values: `submitter`, `approver`, `decision_maker`.
- **Never commit real business data, real passwords or `.env` files.** The sample logins above are test-only.
- Add a `README.md` explaining what your feature does and how to run it.
- Comment the important parts of your code (required by the D4 brief), and remove unused code before submission.

## For AI assistants working in this repo

Read this file first, then `database/README.md` (tables, columns, allowed values) and the `README.md` in each service folder. Only create or change files inside the member's own feature package in `android-app/` or `java-api/`, or their own `team-submissions/<name>/` folder. Never create or alter database tables, never commit real business data or `.env` files, and use only Moving Average and Exponential Smoothing for forecasting (no ARIMA or machine learning).
