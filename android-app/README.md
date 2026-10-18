# Android app

The SME Budget mobile app (Java). It talks to the Spring Boot API in `java-api/`.

| Package | Owner | What |
|---|---|---|
| `auth/` | Dean | Login screen, session storage, logout |
| `home/` | Dean | Home screen after login (role-based menu to come) |
| `network/` | Dean (shared) | `ApiClient` (Retrofit) and `ApiErrors`. Ask Dean before changing. |
| `submissions/` | Aiden | Budget submission screens (to add) |
| `targets/` | Boitu | KPI target and forecast screens (to add) |

The theme and colours in `res/values/themes.xml` and `colors.xml` are shared too.

## Login and logout (Dean)

- **Login screen:** checks that the email looks valid and the password isn't empty before calling the API, shows a spinner while waiting, and shows the server's message (e.g. "Incorrect email or password.") or a "cannot reach the server" message if the API is down.
- **Session:** after login the token and user details are saved in the app's private storage (`SessionManager`); the password is never saved. Next time the app opens, it goes straight to Home.
- **Home:** shows the user's name and role. On opening it checks with the API that the session is still valid; if not, it returns to login with "Your session has expired."
- **Logout:** asks for confirmation, ends the session on the server, clears it on the phone and returns to login. Back can't return to Home afterwards.

## Run it

1. Start the database and the API (see `java-api/README.md`).
2. In Android Studio: **File > Open** and choose the `android-app` folder (not the repo root). Let Gradle sync.
3. Run on an emulator. Log in with `submitter@example.com` / `Test@1234` (or the approver or decision maker accounts in the main README).

The app calls `http://10.0.2.2:8080/`, which is your PC as seen from the emulator. Plain `http` is allowed only for that address (see `res/xml/network_security_config.xml`). A real phone can't reach your PC's API.

## For Aiden and Boitu: calling the API from your screens

```java
// 1. An interface for your endpoints, in your own package:
public interface SubmissionsApi {
    @GET("api/submissions")
    Call<List<SubmissionDto>> list();
}

// 2. In your activity. The login token is added automatically.
SubmissionsApi api = ApiClient.create(this, SubmissionsApi.class);

// 3. The logged-in user:
SessionManager session = new SessionManager(this);
session.getRole();    // "submitter", "approver" or "decision_maker" (see Roles)
session.getUserId();
session.getUnitId();
```

For error messages from the API use `ApiErrors.message(response, getString(R.string.error_unexpected))`.
