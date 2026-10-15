package com.smebudget.app.auth;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.smebudget.app.R;
import com.smebudget.app.databinding.ActivityLoginBinding;
import com.smebudget.app.home.HomeActivity;
import com.smebudget.app.network.ApiClient;
import com.smebudget.app.network.ApiErrors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Login screen (Dean). Checks the fields, calls POST /api/auth/login, saves the session and opens Home.
 * If a session is already saved, it goes straight to Home.
 */
public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private SessionManager session;
    private Call<LoginResponse> loginCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);

        if (session.isLoggedIn()) {
            openHome();
            return;
        }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.loginButton.setOnClickListener(v -> attemptLogin());
        // Pressing "Done" on the keyboard in the password field also logs in.
        binding.passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
    }

    /** Validates the inputs on the phone first, so obvious mistakes don't need a server call. */
    private void attemptLogin() {
        binding.emailLayout.setError(null);
        binding.passwordLayout.setError(null);
        binding.errorText.setVisibility(View.GONE);

        String email = textOf(binding.emailInput).trim();
        String password = textOf(binding.passwordInput);
        boolean valid = true;

        if (email.isEmpty()) {
            binding.emailLayout.setError(getString(R.string.error_email_required));
            valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailLayout.setError(getString(R.string.error_email_invalid));
            valid = false;
        }
        if (password.isEmpty()) {
            binding.passwordLayout.setError(getString(R.string.error_password_required));
            valid = false;
        }
        if (!valid) {
            return;
        }

        setLoading(true);
        loginCall = ApiClient.create(this, AuthApi.class).login(new LoginRequest(email, password));
        loginCall.enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(@NonNull Call<LoginResponse> call, @NonNull Response<LoginResponse> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                LoginResponse body = response.body();
                if (response.isSuccessful() && body != null && body.token != null) {
                    session.save(body);
                    String name = body.user != null ? body.user.fullName : email;
                    Toast.makeText(LoginActivity.this, getString(R.string.welcome_toast, name), Toast.LENGTH_SHORT).show();
                    openHome();
                } else {
                    // e.g. "Incorrect email or password." from the API
                    showError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<LoginResponse> call, @NonNull Throwable t) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                showError(getString(R.string.error_no_connection));
            }
        });
    }

    /** Shows the spinner and stops double taps while the request is running. */
    private void setLoading(boolean loading) {
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.loginButton.setEnabled(!loading);
        binding.emailInput.setEnabled(!loading);
        binding.passwordInput.setEnabled(!loading);
    }

    private void showError(String message) {
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    /** Opens Home as a fresh task, so pressing back there leaves the app instead of returning here. */
    private void openHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private static String textOf(EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    @Override
    protected void onDestroy() {
        if (loginCall != null) {
            loginCall.cancel();
        }
        super.onDestroy();
    }
}
