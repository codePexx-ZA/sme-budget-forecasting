# Android app

The SME Budget mobile app (Java). It talks to the Spring Boot API in `java-api/`.

| Package | Owner | What |
|---|---|---|
| `auth/` | Dean | Login and create account screens, session storage, logout |
| `home/` | Dean | Home screen after login with a menu for each role |
| `network/` | Dean (shared) | `ApiClient` (Retrofit) and `ApiErrors`. Ask Dean before changing. |
| `submissions/` | Aiden | Budget submission screens. D4 scope for this package is complete |
| `targets/` | Boitu | KPI targets, forecasts, warnings and the meeting report |

The theme and colours in `res/values/themes.xml` and `colors.xml` are shared too.

## Login and logout (Dean)

- **Login screen:** checks that the email looks valid and the password isn't empty before calling the API, shows a spinner while waiting, and shows the server's message (e.g. "Incorrect email or password.") or a "cannot reach the server" message if the API is down.
- **Session:** after login the token and user details are saved in the app's private storage (`SessionManager`); the password is never saved. Next time the app opens, it goes straight to Home.
- **Home:** shows the user's name, role and a menu of only the screens that role can use (see below). On opening it checks with the API that the session is still valid; if not, it returns to login with "Your session has expired."
- **Logout:** asks for confirmation, ends the session on the server, clears it on the phone and returns to login. Back can't return to Home afterwards.

### Home menu by role

| Role | Menu |
|---|---|
| Submitter | Budget submissions: create, edit and submit income statements |
| Approver | Review submissions: approve or reject waiting submissions |
| Decision Maker | Budget submissions (view only), KPI targets, forecasts and economic warnings, then the meeting report |

The meeting report and the forecast screen use a section heading, then a label with its figure on the same row. The emailed report stays plain text.

Hiding a menu option is only for convenience. The API checks the role again on every request, so a user can't reach another role's actions by other means.

### Navigation and the back button

- Login and Home each start a fresh task. After logging in, back from Home closes the app instead of returning to login. After logging out, back can't reopen Home.
- Feature screens open on top of Home. Their toolbar arrow and the phone's back button both return to the previous screen, and lists reload when you come back.
- If the session expires on any screen (API returns 401), the app clears it and returns to login.

### Create account

"Create account" on the login screen opens a sign-up form (name, email, password, confirm password). The app and the API both check the inputs: a valid email, a password of at least 8 characters with a letter and a number, and matching passwords. An email that is already registered gets "An account with this email already exists." After signing up, the user is logged in and lands on Home.

**New accounts are always Submitters** in the sample business. The API sets the role, not the app, so nobody can make themselves an Approver or Decision Maker by signing up. Those accounts are set up by the business (for the prototype, in `database/init/03_seed.sql`).

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
