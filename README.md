# SME Budget Forecasting System

ITMDA3-34 group project, Eduvos, Group 1, 2026. D4 implementation.

The written report is [Deliverable 4 Final Document.pdf](Deliverable%204%20Final%20Document.pdf), in this same folder.

A submitter enters past income statements. The system forecasts each category with Moving Average and Exponential Smoothing, shows warnings from inflation, fuel and electricity data, and lets an approver accept or reject a budget. A decision maker sets KPI targets and reads a meeting report. This is a budgeting tool, not an accounting system. There is no ARIMA, no machine learning, and no Excel importer.

## Start the system

You need Docker Desktop and Android Studio. From `csharp-ingestion/`:

```powershell
Copy-Item .env.example .env
docker compose up -d --build
```

Copy the settings file only the first time. The sample database password in that file is `budgetpass`.

This starts PostgreSQL, the C# alerts service, the Python forecast service, and the Spring Boot API. Do not also run `gradlew bootRun`. That uses the same port, 8080.

| Service | On this PC |
|---|---|
| Java API, which the phone uses | http://localhost:8080 |
| C# alerts, Swagger | http://localhost:5080/swagger |
| Python forecasts, docs | http://localhost:5090/docs |
| PostgreSQL | localhost:5432, database `budget_prototype`, user `budget` |

Then open `android-app/` in Android Studio and press Run. The emulator already calls the API at `http://10.0.2.2:8080`.

`docker compose stop` keeps the data. `docker compose down -v` deletes the database. The next start loads the sample data again from `database/init/`.

## Log in

Every sample password is `Test@1234`. The business is a fictional hardware store.

| Role | Email | What to open |
|---|---|---|
| Submitter | `submitter@example.com` | Budget submissions. Edit the draft, then submit it. |
| Approver | `approver@example.com` | Review submissions. Approve or reject the one that is waiting. |
| Decision Maker | `decisionmaker@example.com` | KPI targets, then Forecasts and alerts. Tap **Run forecast**, then open Meeting report. |

Create account on the login screen makes another submitter. Log out from Home. Back on Home closes the app.

The sample data already has five years of approved history, one submission waiting for approval, one draft, three KPI targets, and three open warnings (fuel, electricity, headline inflation). Forecast amounts on the meeting report stay `n/a` until **Run forecast**.

On that report, a green box means the forecast is a better result than last year. A red box means it is worse. A higher cost is red. Last year's figure and the typed target stay plain.

## Where to look

| Folder | What it is |
|---|---|
| `android-app/` | The phone app. Open this in Android Studio. |
| `java-api/` | Spring Boot API. Login, submissions, KPI targets, forecasts, warnings, meeting report. |
| `database/init/` | The only place tables are created. `01_schema.sql` is the schema, `03_seed.sql` is the sample data. |
| `csharp-ingestion/` | Fetches World Bank and OECD figures and raises a warning when a yearly change crosses a threshold. Also holds `docker-compose.yml`. |
| `python-forecast/` | Moving Average and Exponential Smoothing. |

Inside the app and the API, each person's work is in its own package:

| Package | Person | What it does |
|---|---|---|
| `auth/`, `home/`, `network/`, `config/` | Dean | Login, create account, logout, session token, role menu, shared security and theme |
| `submissions/` | Aiden | Create, view, edit and delete budget submissions and line items. Submit, approve and reject. |
| `targets/` | Boitu | KPI targets, forecasts, economic warnings, and the meeting report |
| `database/`, `csharp-ingestion/`, `python-forecast/`, Docker | Liam | Database, sample data, external warnings, forecasting, and running the whole system |

Passwords are stored as BCrypt hashes. The API checks the role again on every create, update, delete, submit, approve, reject, forecast and report request. A submitter cannot open the meeting report through the API. Java does not create or alter tables (`ddl-auto=validate`). No `.env` file is included. Copy `.env.example` as shown above.
