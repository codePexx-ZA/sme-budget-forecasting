package com.smebudget.app.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smebudget.app.R;
import com.smebudget.app.auth.AuthApi;
import com.smebudget.app.auth.LoginActivity;
import com.smebudget.app.auth.Roles;
import com.smebudget.app.auth.SessionManager;
import com.smebudget.app.auth.UserDto;
import com.smebudget.app.databinding.ActivityHomeBinding;
import com.smebudget.app.network.ApiClient;
import com.smebudget.app.submissions.SubmissionListActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Home screen after login (Dean). Shows who is logged in and has the logout button.
 * Budget submissions (Aiden) is linked from menuContainer. Boitu's screens can be added there too.
 */
public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private SessionManager session;
    private AuthApi authApi;
    private Call<UserDto> meCall;
    private Call<Void> logoutCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);

        // Only logged-in users may see this screen.
        if (!session.isLoggedIn()) {
            goToLogin(0);
            return;
        }

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        authApi = ApiClient.create(this, AuthApi.class);

        showUser();
        binding.submissionsButton.setOnClickListener(v ->
                startActivity(new Intent(this, SubmissionListActivity.class)));
        binding.logoutButton.setOnClickListener(v -> confirmLogout());
        checkSessionStillValid();
    }

    private void showUser() {
        binding.welcomeText.setText(getString(R.string.home_welcome, session.getFullName()));
        binding.roleText.setText(getString(R.string.home_role, Roles.label(this, session.getRole())));
    }

    /** Asks the API whether the saved token still works (it stops working after expiry or an API restart). */
    private void checkSessionStillValid() {
        meCall = authApi.me();
        meCall.enqueue(new Callback<UserDto>() {
            @Override
            public void onResponse(@NonNull Call<UserDto> call, @NonNull Response<UserDto> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    session.saveUser(response.body());
                    showUser();
                } else if (response.code() == 401) {
                    session.clear();
                    goToLogin(R.string.session_expired);
                }
            }

            @Override
            public void onFailure(@NonNull Call<UserDto> call, @NonNull Throwable t) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) {
                    return;
                }
                Toast.makeText(HomeActivity.this, R.string.error_no_connection, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.logout_confirm_title)
                .setMessage(R.string.logout_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.log_out, (dialog, which) -> logout())
                .show();
    }

    /**
     * Ends the session on the server, then forgets it on the phone. The phone side is cleared even if
     * the server can't be reached, so the user is never stuck logged in.
     */
    private void logout() {
        binding.progress.setVisibility(View.VISIBLE);
        binding.logoutButton.setEnabled(false);

        logoutCall = authApi.logout();
        logoutCall.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                finishLogout();
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                if (!call.isCanceled()) {
                    finishLogout();
                }
            }
        });
    }

    private void finishLogout() {
        session.clear();
        if (!isFinishing() && !isDestroyed()) {
            goToLogin(R.string.logged_out);
        }
    }

    /** Opens Login as a fresh task, so back can't return to Home after logging out. */
    private void goToLogin(@StringRes int messageRes) {
        if (messageRes != 0) {
            Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show();
        }
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (meCall != null) {
            meCall.cancel();
        }
        if (logoutCall != null) {
            logoutCall.cancel();
        }
        super.onDestroy();
    }
}
