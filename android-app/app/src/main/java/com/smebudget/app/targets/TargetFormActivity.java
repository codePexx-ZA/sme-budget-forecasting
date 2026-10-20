package com.smebudget.app.targets;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smebudget.app.R;
import com.smebudget.app.auth.Roles;
import com.smebudget.app.auth.SessionManager;
import com.smebudget.app.databinding.ActivityTargetFormBinding;
import com.smebudget.app.network.ApiClient;
import com.smebudget.app.network.ApiErrors;

import java.math.BigDecimal;
import java.time.LocalDate;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Create or edit one KPI target (Boitu). The decision maker role is checked here and again by the API.
 */
public class TargetFormActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "targetId";

    private ActivityTargetFormBinding binding;
    private TargetsApi api;
    private String targetId;
    private Call<TargetDto> loadCall;
    private Call<TargetDto> saveCall;
    private Call<Void> deleteCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn() || !Roles.DECISION_MAKER.equals(session.getRole())) {
            finish();
            return;
        }

        binding = ActivityTargetFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        api = ApiClient.create(this, TargetsApi.class);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.saveButton.setOnClickListener(v -> save());

        targetId = getIntent().getStringExtra(EXTRA_ID);
        if (targetId != null) {
            binding.toolbar.setTitle(R.string.target_edit);
            binding.deleteButton.setVisibility(View.VISIBLE);
            binding.deleteButton.setOnClickListener(v -> confirmDelete());
            load();
        }
    }

    private void load() {
        setBusy(true);
        loadCall = api.get(targetId);
        loadCall.enqueue(new Callback<TargetDto>() {
            @Override
            public void onResponse(@NonNull Call<TargetDto> call, @NonNull Response<TargetDto> response) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                setBusy(false);
                if (SessionNav.expired(TargetFormActivity.this, response)) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    TargetDto target = response.body();
                    binding.metricInput.setText(target.metricName);
                    binding.valueInput.setText(target.targetValue == null ? "" : target.targetValue.toPlainString());
                    binding.unitInput.setText(target.measurementUnit);
                    binding.periodInput.setText(target.targetPeriod);
                } else {
                    showError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<TargetDto> call, @NonNull Throwable t) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                setBusy(false);
                showError(getString(R.string.error_no_connection));
            }
        });
    }

    private void save() {
        binding.errorText.setVisibility(View.GONE);
        String metric = text(binding.metricInput.getText());
        String valueText = text(binding.valueInput.getText());
        String unit = text(binding.unitInput.getText());
        String period = text(binding.periodInput.getText());

        if (metric.isEmpty()) {
            showError(getString(R.string.target_metric_required));
            return;
        }
        try {
            new BigDecimal(valueText);
        } catch (NumberFormatException ex) {
            showError(getString(R.string.target_value_invalid));
            return;
        }
        if (unit.isEmpty()) {
            showError(getString(R.string.target_unit_required));
            return;
        }
        try {
            LocalDate.parse(period);
        } catch (Exception ex) {
            showError(getString(R.string.target_period_invalid));
            return;
        }

        TargetBody body = new TargetBody(metric, valueText, unit, period);
        setBusy(true);
        saveCall = targetId == null ? api.create(body) : api.update(targetId, body);
        saveCall.enqueue(new Callback<TargetDto>() {
            @Override
            public void onResponse(@NonNull Call<TargetDto> call, @NonNull Response<TargetDto> response) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                setBusy(false);
                if (SessionNav.expired(TargetFormActivity.this, response)) {
                    return;
                }
                if (response.isSuccessful()) {
                    Toast.makeText(TargetFormActivity.this, R.string.target_saved, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    showError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<TargetDto> call, @NonNull Throwable t) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                setBusy(false);
                showError(getString(R.string.error_no_connection));
            }
        });
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.target_delete_title)
                .setMessage(R.string.target_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.target_delete, (dialog, which) -> delete())
                .show();
    }

    private void delete() {
        setBusy(true);
        deleteCall = api.delete(targetId);
        deleteCall.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                setBusy(false);
                if (SessionNav.expired(TargetFormActivity.this, response)) {
                    return;
                }
                if (response.isSuccessful()) {
                    Toast.makeText(TargetFormActivity.this, R.string.target_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    showError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                setBusy(false);
                showError(getString(R.string.error_no_connection));
            }
        });
    }

    private void setBusy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.saveButton.setEnabled(!busy);
        binding.deleteButton.setEnabled(!busy);
    }

    private void showError(String message) {
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    private static String text(CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }

    @Override
    protected void onDestroy() {
        if (loadCall != null) {
            loadCall.cancel();
        }
        if (saveCall != null) {
            saveCall.cancel();
        }
        if (deleteCall != null) {
            deleteCall.cancel();
        }
        super.onDestroy();
    }
}
