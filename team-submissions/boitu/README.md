# KPI targets, forecasts, alerts and the meeting report (Boitu)

**D4 status: complete.** Do not rebuild this module.

CRUD for KPI targets, a decision-maker screen for Moving Average and Exponential Smoothing plus economic warnings, and a meeting report that groups the approved year, both forecasts, results, targets and open warnings. The report screen and the forecast screen use section headings and a label on the left with the figure on the right. Email is the phone's own share sheet. This app does not send mail and does not store a mail password.

It lives in the shared Android app and Spring Boot API. Login and navigation stay in Dean's packages. The forecast maths stays in `python-forecast/`. The warning rules stay in `csharp-ingestion/`. The Java API only reads saved forecast rows and open alerts for the report. It does not calculate a forecast.

| | |
|---|---|
| Android screens | `android-app/.../com/smebudget/app/targets/` |
| API | `java-api/.../com/smebudget/api/targets/` |
| Tests | `TargetServiceTest`, `InsightsParseTest`, `ReportServiceTest` (no database) |

A target is a metric name, a value, a unit (`ZAR` or `percent`) and a period. The same name cannot be saved twice for the same period. Only a decision maker can create, edit or delete. Anyone logged in can read the targets for their own business unit.

Forecasts are not calculated in Java. `POST /api/insights/forecasts` asks the Python service to run both methods on the unit's approved history (annual, next period, window 3, alpha 0.5). `GET /api/insights/alerts` reads the C# warning list.

The screens open from Home for a decision maker: **Meeting report**, **KPI targets** and **Forecasts and alerts**.

`.\gradlew.bat bootRun` needs JDK 17. On this PC the default terminal Java is 11. Set `JAVA_HOME` to `C:\Program Files\Java\jdk-17` first. Restarting the API drops every login.

## Run only this part

1. Start PostgreSQL, the C# API and the Python API (see the main README): from `csharp-ingestion/`, `docker compose up -d --build`.
2. In `java-api/`, copy `.env.example` to `.env` and set `POSTGRES_PASSWORD` to the same value as `csharp-ingestion/.env`.
3. Run the tests (no database needed):

```bash
cd java-api
./gradlew test --tests com.smebudget.api.targets.TargetServiceTest --tests com.smebudget.api.targets.InsightsParseTest --tests com.smebudget.api.targets.ReportServiceTest
```

4. Start the API: `./gradlew bootRun` (http://localhost:8080).
5. Open `android-app/` in Android Studio and run it on an emulator. Log in as `decisionmaker@example.com` / `Test@1234`.

The sample unit already has three KPI targets. Forecasts need the Python container. Alerts need a completed C# ingest (`POST http://localhost:5080/api/ingestion/run`).

## Endpoints

All of these need `Authorization: Bearer <token>` from `POST /api/auth/login`.

| Method | Path | Who |
|---|---|---|
| GET | `/api/targets` | any logged-in user, own business unit |
| GET | `/api/targets/{id}` | any logged-in user, own business unit |
| POST | `/api/targets` | decision maker |
| PUT | `/api/targets/{id}` | decision maker |
| DELETE | `/api/targets/{id}` | decision maker |
| GET | `/api/insights/forecasts` | any logged-in user, own business unit |
| POST | `/api/insights/forecasts` | decision maker, runs both forecast methods |
| GET | `/api/insights/alerts` | any logged-in user |
| GET | `/api/reports/meeting` | any logged-in user, own business unit |

`GET /api/reports/meeting` is the meeting report: the latest approved year, both saved forecasts, gross / operating / net profit, KPI targets and open economic warnings. Drafts and submissions still waiting are named and left out of the figures. The Android screen **Meeting report** can email that text through the phone's own share sheet.
