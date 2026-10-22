# Java API (Spring Boot)

The API the Android app talks to. It reads and writes the shared PostgreSQL database (`database/`).

| Package | Owner | What |
|---|---|---|
| `auth/` | Dean | Sign-up, login, logout, current user, session tokens |
| `config/` | Dean (shared) | Security rules. Ask Dean before changing. |
| `common/` | Dean (shared) | Error handling: every error comes back as `{"message": "..."}` |
| `submissions/` | Aiden | Budget submissions. D4 scope for this package is complete |
| `targets/` | Boitu | KPI targets, forecasts, alerts and the meeting report. See `team-submissions/boitu/README.md`. D4 scope for this package is complete |

## Sign-up, login and logout (Dean)

| Endpoint | Login needed | Body / reply |
|---|---|---|
| `POST /api/auth/register` | No | Body `{"fullName", "email", "password"}`. Creates a **submitter** and replies `201` with the same body as login. `409` if the email is taken, `400` if a field breaks the rules |
| `POST /api/auth/login` | No | Body `{"email": "...", "password": "..."}`. Reply `{"token", "expiresAt", "user": {"userId", "fullName", "email", "role", "unitId"}}` |
| `POST /api/auth/logout` | Yes | Ends the session; the token stops working. Reply `{"message": "You have been logged out."}` |
| `GET /api/auth/me` | Yes | The logged-in user's details |

How it works:

- Passwords are stored and checked as BCrypt hashes in `app_user.password_hash`. Plain passwords are never stored or logged.
- Sign-up rules: valid email, password 8 to 72 characters with at least one letter and one number. The role is always `submitter`, set by the server, and the account joins the business unit in `NEW_ACCOUNT_UNIT_ID` (default: the sample business).
- A wrong password and an unknown email get the same reply (`401 Incorrect email or password.`), so nobody can find out which emails exist. Inactive users get `403`.
- A successful login returns a random token. The app sends it on every request as `Authorization: Bearer <token>`. The server remembers tokens in memory for `SESSION_HOURS` (default 8). Logout deletes the token. Restarting the API logs everyone out.
- Every endpoint except login and sign-up returns `401 {"message": "Please log in to continue."}` without a valid token.

## Run it (Windows)

`docker compose up -d --build` from `csharp-ingestion/` starts this API in the `java-api` container on port 8080. Use the steps below only when you want to run it on the host instead. Stop `budget-java-api` first.

1. Start the database: in `csharp-ingestion/`, run `docker compose up -d` (see the main README).
2. In `java-api/`, copy the settings file once and set `POSTGRES_PASSWORD` to the same password as `csharp-ingestion/.env`:

   ```powershell
   Copy-Item .env.example .env
   ```

3. Start the API (needs JDK 17 or newer; the first run downloads Gradle and libraries):

   ```powershell
   .\gradlew.bat bootRun
   ```

   It runs on http://localhost:8080. The emulator reaches it at `http://10.0.2.2:8080`.

4. Run the tests: `.\gradlew.bat test`

Quick check from PowerShell:

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login `
  -ContentType "application/json" -Body '{"email":"submitter@example.com","password":"Test@1234"}'
$headers = @{ Authorization = "Bearer $($login.token)" }
Invoke-RestMethod -Uri http://localhost:8080/api/auth/me -Headers $headers        # your user
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/logout -Headers $headers
Invoke-RestMethod -Uri http://localhost:8080/api/auth/me -Headers $headers        # now 401
```

## For Aiden and Boitu: using login in your endpoints

Every endpoint you add is already protected: only logged-in users can call it. To get the user, or to allow only certain roles:

```java
@PostMapping("/api/submissions/{id}/approve")
@PreAuthorize("hasRole('APPROVER')")          // roles: SUBMITTER, APPROVER, DECISION_MAKER
public SubmissionResponse approve(@PathVariable String id,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
    // user.userId() is the app_user.user_id, e.g. for approval_action.approved_by
}
```

To send an error message to the app, `throw new ApiException(HttpStatus.BAD_REQUEST, "Period end must be after period start.");`

Rules: never create tables from Java (`ddl-auto=validate` stops it), and never hard-code passwords; put settings in `.env`.
