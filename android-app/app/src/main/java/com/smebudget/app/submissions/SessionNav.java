package com.smebudget.app.submissions;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.smebudget.app.R;
import com.smebudget.app.auth.LoginActivity;
import com.smebudget.app.auth.SessionManager;

import retrofit2.Response;

/** Sends the user back to login when the API says the session is no longer valid. */
final class SessionNav {

    private SessionNav() {
    }

    static boolean expired(Activity activity, Response<?> response) {
        if (response.code() != 401) {
            return false;
        }
        new SessionManager(activity).clear();
        Toast.makeText(activity, R.string.session_expired, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
        return true;
    }
}
