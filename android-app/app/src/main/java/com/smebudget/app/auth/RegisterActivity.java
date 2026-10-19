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
import com.smebudget.app.databinding.ActivityRegisterBinding;
import com.smebudget.app.home.HomeActivity;
import com.smebudget.app.network.ApiClient;
import com.smebudget.app.network.ApiErrors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Create account screen (Dean). Checks the fields, calls POST /api/auth/register and, on success,
 * saves the session and opens Home, the same as a login. New accounts are always submitters; the API
 * decides the role, not the app.
 */
public class RegisterActivity extends AppCompatActivity {

    /** Same rule as the API: at least 8 characters, with a letter and a number. */
    private static final int MIN_PASSWORD_LENGTH = 8;

    private ActivityRegisterBinding binding;
    private SessionManager session;
    private Call<LoginResponse> registerCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // The toolbar arrow does the same as the phone's back button: return to login.
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.registerButton.setOnClickListener(v -> attemptRegister());
        binding.confirmInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptRegister();
                return true;
            }
            return false;
        });
    }

    /** Validates on the phone first; the API checks the same rules again. */
    private void attemptRegister() {
        binding.nameLayout.setError(null);
        binding.emailLayout.setError(null);
        binding.passwordLayout.setError(null);
        binding.confirmLayout.setError(null);
        binding.errorText.setVisibility(View.GONE);

        String name = textOf(binding.nameInput).trim();
        String email = textOf(binding.emailInput).trim();
        String password = textOf(binding.passwordInput);
        String confirm = textOf(binding.confirmInput);
        boolean valid = true;

        if (name.isEmpty()) {
            binding.nameLayout.setError(getString(R.string.error_name_required));
            valid = false;
        }
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
        } else if (password.length() < MIN_PASSWORD_LENGTH) {
            binding.passwordLayout.setError(getString(R.string.error_password_short));
            valid = false;
        } else if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            binding.passwordLayout.setError(getString(R.string.error_password_letter_number));
            valid = false;
        }
        if (!confirm.equals(password)) {
            binding.confirmLayout.setError(getString(R.string.error_password_mismatch));
            valid = false;
        }
        if (!valid) {
            return;
        }

        setLoading(true);
        registerCall = ApiClient.create(this, AuthApi.class).register(new RegisterRequest(name, email, password));
        registerCall.enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(@NonNull Call<LoginResponse> call, @NonNull Response<LoginResponse> response) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                setLoading(false);
                LoginResponse body = response.body();
                if (response.isSuccessful() && body != null && body.token != null) {
                    session.save(body);
                    Toast.makeText(RegisterActivity.this, getString(R.string.account_created, name), Toast.LENGTH_SHORT).show();
                    openHome();
                } else {
                    // e.g. "An account with this email already exists. Please log in instead."
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

    private void setLoading(boolean loading) {
        binding.progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.registerButton.setEnabled(!loading);
        binding.nameInput.setEnabled(!loading);
        binding.emailInput.setEnabled(!loading);
        binding.passwordInput.setEnabled(!loading);
        binding.confirmInput.setEnabled(!loading);
    }

    private void showError(String message) {
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    /** Opens Home as a fresh task, so back can't return to the sign-up or login screens. */
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
        if (registerCall != null) {
            registerCall.cancel();
        }
        super.onDestroy();
    }
}
