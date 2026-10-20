# SME Budget Forecasting System

ITMDA3-34 group project (Eduvos, Group 1, 2026). A lightweight budget forecasting app for South African SMEs.

Businesses upload past income statements. The system forecasts each income statement category with Moving Average and Exponential Smoothing, shows warnings from external economic data (inflation, fuel, electricity), and lets managers approve budgets, targets and KPIs. It supports budgeting decisions. It is **not** an accounting system. No ARIMA or machine learning.

**Roles.** The Submitter uploads data, the Approver approves or rejects it, and the Decision Maker views forecasts, targets and KPIs.

## Deadlines

| Deliverable | Due |
|---|---|
| D4 Implementation (50 marks) | **Fri 23 Oct 2026, 23:59** |
| D5 | Fri 30 Oct 2026, 23:59 |
| D6 | Fri 13 Nov 2026, 23:59 |

D4 team milestones:

| Date | Milestone |
|---|---|
| Tue 13 Oct | Dean pushes the one shared Android Studio project (`android-app/`) and Spring Boot project (`java-api/`) with login and a navigation shell. Aiden and Boitu build their modules inside these, not in separate projects. |
| Wed 14 to Thu 15 Oct | Liam checks that the app starts and logs in with `submitter@example.com` / `Test@1234`. |
| Sun 18 Oct | All features pushed and handed over to Liam (keep pushing weekly before this). |
| 18 to 23 Oct | Liam integrates, adds Spring Boot to Docker Compose, fixes issues and does the final review. |
| Fri 23 Oct, 23:59 | D4 submitted. |

D4 needs a working Android mobile app, not just designs:

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
| **Liam** | Database, sample data, C# external data service, Python forecasting service, Docker (done). The app already reaches forecasts and alerts through `java-api/`. Next: Spring Boot in Docker Compose, pull request reviews, final build | 3, 7, integration |
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
| Spring Boot in Docker Compose | `csharp-ingestion/docker-compose.yml` | Docker | Liam | Not started. `java-api/` exists |
| Excel income statement importer | `csharp-ingestion/` | C# | Liam | After D4 (not required) |

## Where work stopped (20 Oct 2026)

Paused overnight. Continue in a new session from this section. Do not rebuild Dean's login, Aiden's submissions, or Boitu's targets, forecasts, alerts or meeting report.

Aiden's D4 module is complete: create, view, edit, delete, line items, submit, approve and reject. Nothing in `submissions/` is left open.

Still open before Friday's D4 hand-in:

1. **Liam:** add the Spring Boot API to `csharp-ingestion/docker-compose.yml`. From the containers, the forecast URL is `http://forecast:5090` and the alerts URL is `http://api:5080` (`FORECAST_BASE_URL` and `ALERTS_BASE_URL` in `java-api/.env.example`). On a PC they stay `http://localhost:5090` and `http://localhost:5080`.
2. Click through submitter, approver and decision maker on a fresh emulator run. Forecast columns on the meeting report stay `n/a` until **Run forecast** has been used.
3. This handoff is on `main`. Continue from here; do not rebuild the Done modules.

Run notes for the next session:

- Start Docker from `csharp-ingestion/` with `docker compose up -d --build`.
- Start the API from `java-api/` with JDK 17. The default terminal Java on this PC is 11, and Gradle will refuse to start. Set `JAVA_HOME` to `C:\Program Files\Java\jdk-17`, then `.\gradlew.bat bootRun`.
- Restarting the API logs everyone out. The Android app must be run again from Android Studio after layout changes. Use the full Run button, not Apply Changes.
- Decision maker login for the new screens: `decisionmaker@example.com` / `Test@1234`.
- The live database may differ from `03_seed.sql`: the March-May 2026 submission was approved during testing (comment `test`). `docker compose down -v` then `docker compose up -d` restores the seed, and deletes the database. Re-run `POST http://localhost:5080/api/ingestion/run` afterwards so the warnings come back.

## Repo structure

```text
database/                 shared PostgreSQL schema and sample data (the only place tables are created)
csharp-ingestion/         C# service: World Bank and OECD data, warning rules, docker-compose.yml
python-forecast/          Python service: Moving Average and Exponential Smoothing
android-app/              Android Studio project (Java), created by Dean
java-api/                 Spring Boot API: login, submissions, KPI targets, created by Dean
team-submissions/         personal notes, experiments and individual write-ups per member
```

Inside `android-app/` and `java-api/`, each member works in their own feature package, for example `auth/` (Dean), `submissions/` (Aiden) and `targets/` (Boitu). Dean owns the shared files (navigation, theme, security config), so ask him before changing them. Do not edit `database/`, `csharp-ingestion/` or `python-forecast/`. Ask Liam if something there needs to change.

## Run everything

You need Docker Desktop running. From `csharp-ingestion/`:

```powershell
Copy-Item .env.example .env      # first time only
docker compose up -d --build
```

| Service | URL from your PC | URL from the Android emulator |
|---|---|---|
| C# external data API | http://localhost:5080/swagger | `http://10.0.2.2:5080` |
| Python forecasting API | http://localhost:5090/docs | `http://10.0.2.2:5090` |
| PostgreSQL | localhost:5432, database `budget_prototype`, user `budget` | Not reachable |

All three ports are bound to `127.0.0.1`, so other devices on your network (including a real phone) cannot reach them; only your PC and the emulator can. The emulator cannot reach `localhost` on your PC, so use `10.0.2.2` instead. Android also blocks plain `http://` by default. For local development, add `android:usesCleartextTraffic="true"` to the `<application>` tag in `AndroidManifest.xml`.

`docker compose stop` stops the containers and keeps the data. `docker compose down -v` **deletes** the database, and Docker rebuilds it from `database/init/` on the next start. Run it once after pulling `03_seed.sql` to load the sample data.

## Sample logins (fictional test data)

`database/init/03_seed.sql` creates a fictional business ("Sample Hardware Store") with 5 years of approved income statement history, one submission waiting for approval, one draft and three KPI targets. All figures are made up and only for testing until real participant data is available.

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
- In Spring Boot (`java-api/`), read secrets from environment variables and never hard-code them, for example `spring.datasource.password=${POSTGRES_PASSWORD}` and `jwt.secret=${JWT_SECRET}`. Add placeholders such as `JWT_SECRET=change-me` to `.env.example`.
- If a real secret must be shared (for example a cloud database for the demo), send it privately by direct message or password manager, never in the repo, and change it after marking.

## How we work on GitHub

1. Clone once: `git clone https://github.com/codePexx-ZA/sme-budget-forecasting.git`
2. Run `git pull` each time before you start work.
3. Work on your own branch: `git checkout -b dean-login` (or `aiden-submissions`, `boitu-targets`).
4. Commit small, clear changes, for example `git commit -m "Add login screen"`, then `git push`.
5. Open a pull request into `main` when a piece works. Liam reviews and merges it.
6. Push at least once a week. Do not wait until the deadline.

## Rules

- Do not create or change tables in code (in Java, set `spring.jpa.hibernate.ddl-auto=validate`). Ask Liam to change `database/init/01_schema.sql`. Table and column names, and allowed status values, are in `database/README.md`.
- Use exactly these role values: `submitter`, `approver`, `decision_maker`.
- Never commit real business data, real passwords or `.env` files. The sample logins above are for testing only.
- Add a `README.md` that explains what your feature does and how to run it.
- Comment the important parts of your code (the D4 brief requires this), and remove unused code before submission.

## For AI assistants working in this repo

Read this file first, including **Where work stopped**, then `database/README.md` (tables, columns, allowed values) and the `README.md` in each service folder. Only create or change files inside the member's own feature package in `android-app/` or `java-api/`, or their own `team-submissions/<name>/` folder. Never create or alter database tables, never commit real business data or `.env` files, and use only Moving Average and Exponential Smoothing for forecasting (no ARIMA or machine learning). Do not rebuild features whose status is Done.
