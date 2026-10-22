# KPI targets, forecasts, alerts and the meeting report (Boitu)

**D4 status: complete.**

CRUD for KPI targets, a decision-maker screen for Moving Average and Exponential Smoothing plus economic warnings, and a meeting report that groups the approved year, both forecasts, results, targets and open warnings. The report screen and the forecast screen use section headings and a label on the left with the figure on the right. Email is the phone's own share sheet. This app does not send mail and does not store a mail password.

It lives in the shared Android app and Spring Boot API. Login and navigation stay in Dean's packages. The forecast maths stays in `python-forecast/`. The warning rules stay in `csharp-ingestion/`. The Java API only reads saved forecast rows and open alerts for the report. It does not calculate a forecast.

| | |
|---|---|
| Android screens | `android-app/.../com/smebudget/app/targets/` |
| API | `java-api/.../com/smebudget/api/targets/` |
| Tests | `TargetServiceTest`, `InsightsParseTest`, `ReportServiceTest` (no database) |

A target is a metric name, a value, a unit (`ZAR` or `percent`) and a period. The same name cannot be saved twice for the same period. Only a decision maker can create, edit or delete. Anyone logged in can read the targets for their own business unit.

Forecasts are not calculated in Java. `POST /api/insights/forecasts` asks the Python service to run both methods on the unit's approved history (annual, next period, window 3, alpha 0.5). `GET /api/insights/alerts` reads the C# warning list.

The screens open from Home for a decision maker: **KPI targets**, **Forecasts and alerts**, then **Meeting report**. Run the forecast before opening the report so the forecast columns are filled in.

Docker Compose starts this API with the rest of the system. JDK 17 is only needed to run the tests on the host.

## Run only this part

1. Start the system (see the main README): from `csharp-ingestion/`, `docker compose up -d --build`. That starts the database, forecasts, alerts and the API.
2. Run the tests (no database needed, JDK 17):

```bash
cd java-api
./gradlew test --tests com.smebudget.api.targets.TargetServiceTest --tests com.smebudget.api.targets.InsightsParseTest --tests com.smebudget.api.targets.ReportServiceTest
```

3. Open `android-app/` in Android Studio and run it on an emulator. Log in as `decisionmaker@example.com` / `Test@1234`.

The sample unit already has three KPI targets and three open economic warnings. Forecast figures appear after **Run forecast**.

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
