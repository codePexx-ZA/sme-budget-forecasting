# Budget submissions (Aiden Mc Gavigan)

CRUD module for budget submissions and their income-statement line items, plus submit, approve and reject. It lives inside the shared Android app and Spring Boot API. Login, navigation and security stay in Dean's `auth/`, `home/`, `network/` and `config/` packages.

| | |
|---|---|
| Android screens | `android-app/.../com/smebudget/app/submissions/` |
| API | `java-api/.../com/smebudget/api/submissions/` |
| Tests | `SubmissionServiceTest` (no database) |

Amounts are stored in rands. A submission starts as `draft`. The submitter can edit or delete a draft, or a submission that was rejected (editing a rejected one returns it to draft). Submitting it sets `submitted` and `submitted_at`. Only an approver can approve or reject a submission that is waiting. A rejection must include a comment. Approving a submission that replaces an already approved one marks the older one `superseded`. Approved and superseded rows are kept and cannot be deleted.

Gross profit, operating profit and net profit are calculated when a submission is read. They are not stored.

The screens are opened from Home → **Budget submissions**. A submitter can create and edit. An approver sees Approve and Reject on a submission that is waiting. A decision maker can read the list and the detail, including the subtotals.

## Run only this part

1. Start PostgreSQL (see the main README): from `csharp-ingestion/`, `docker compose up -d`.
2. In `java-api/`, copy `.env.example` to `.env` and set `POSTGRES_PASSWORD` to the same value as `csharp-ingestion/.env`.
3. Run Aiden's tests (no database needed):

```bash
cd java-api
./gradlew test --tests com.smebudget.api.submissions.SubmissionServiceTest
```

4. Start the API: `./gradlew bootRun` (http://localhost:8080).
5. Open `android-app/` in Android Studio and run it on an emulator. Log in, then open **Budget submissions**.

Sample logins (password `Test@1234`): `submitter@example.com`, `approver@example.com`, `decisionmaker@example.com`. The sample unit already has an approved history, one submission waiting for approval, and one draft.

## Endpoints

All of these need `Authorization: Bearer <token>` from `POST /api/auth/login`.

| Method | Path | Who |
|---|---|---|
| GET | `/api/submissions/categories` | any logged-in user |
| GET | `/api/submissions` | any logged-in user, own business unit |
| GET | `/api/submissions/{id}` | any logged-in user, own business unit |
| POST | `/api/submissions` | submitter, creates a draft |
| PUT | `/api/submissions/{id}` | submitter, draft or rejected |
| DELETE | `/api/submissions/{id}` | submitter, draft or rejected |
| POST | `/api/submissions/{id}/line-items` | submitter, draft or rejected |
| PUT | `/api/submissions/{id}/line-items/{lineItemId}` | submitter, draft or rejected |
| DELETE | `/api/submissions/{id}/line-items/{lineItemId}` | submitter, draft or rejected |
| POST | `/api/submissions/{id}/submit` | submitter |
| POST | `/api/submissions/{id}/approve` | approver, comment optional |
| POST | `/api/submissions/{id}/reject` | approver, comment required |

On update, leave `lineItems` out to keep the current lines. Send `lineItems: []` to clear them. Every line's period must fall inside the submission period, and a category can appear only once per period.
