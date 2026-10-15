package com.smebudget.app.auth;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers the logged-in user on this phone (Dean).
 *
 * The session token and user details are kept in private app storage that other apps cannot read.
 * The password itself is never stored. Any screen can call, for example,
 * {@code new SessionManager(this).getRole()} to decide what to show.
 */
public class SessionManager {

    private static final String PREFS_NAME = "session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_ROLE = "role";
    private static final String KEY_UNIT_ID = "unit_id";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Saves the token and user after a successful login. */
    public void save(LoginResponse response) {
        prefs.edit().putString(KEY_TOKEN, response.token).apply();
        saveUser(response.user);
    }

    /** Updates the stored user details (e.g. after GET /api/auth/me). */
    public void saveUser(UserDto user) {
        if (user == null) {
            return;
        }
        prefs.edit()
                .putString(KEY_USER_ID, user.userId)
                .putString(KEY_FULL_NAME, user.fullName)
                .putString(KEY_ROLE, user.role)
                .putString(KEY_UNIT_ID, user.unitId)
                .apply();
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getFullName() {
        return prefs.getString(KEY_FULL_NAME, "");
    }

    /** submitter, approver or decision_maker. */
    public String getRole() {
        return prefs.getString(KEY_ROLE, null);
    }

    public String getUnitId() {
        return prefs.getString(KEY_UNIT_ID, null);
    }

    /** Forgets everything (logout or expired session). */
    public void clear() {
        prefs.edit().clear().apply();
    }
}
