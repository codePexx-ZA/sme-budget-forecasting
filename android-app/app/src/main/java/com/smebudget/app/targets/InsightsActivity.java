package com.smebudget.app.targets;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.smebudget.app.R;
import com.smebudget.app.auth.Roles;
import com.smebudget.app.auth.SessionManager;
import com.smebudget.app.databinding.ActivityInsightsBinding;
import com.smebudget.app.databinding.ItemInsightBinding;
import com.smebudget.app.databinding.ItemMetricRowBinding;
import com.smebudget.app.databinding.ItemWarningBinding;
import com.smebudget.app.network.ApiClient;
import com.smebudget.app.network.ApiErrors;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Decision maker view (Boitu). Shows the latest Moving Average and Exponential Smoothing runs,
 * and the economic warnings from the C# service. Running a forecast asks the API to call Python.
 */
public class InsightsActivity extends AppCompatActivity {

    private ActivityInsightsBinding binding;
    private TargetsApi api;
    private final DecimalFormat amount = new DecimalFormat("#,##0.00");
    private Call<List<ForecastDto>> forecastCall;
    private Call<List<AlertDto>> alertCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            finish();
            return;
        }

        binding = ActivityInsightsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        api = ApiClient.create(this, TargetsApi.class);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        boolean decisionMaker = Roles.DECISION_MAKER.equals(session.getRole());
        binding.runButton.setVisibility(decisionMaker ? View.VISIBLE : View.GONE);
        binding.runButton.setOnClickListener(v -> runForecast());
        load();
    }

    private void load() {
        setBusy(true);
        binding.forecastError.setVisibility(View.GONE);
        binding.alertError.setVisibility(View.GONE);
        forecastCall = api.forecasts();
        forecastCall.enqueue(new Callback<List<ForecastDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<ForecastDto>> call, @NonNull Response<List<ForecastDto>> response) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                if (SessionNav.expired(InsightsActivity.this, response)) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    showForecasts(response.body());
                } else {
                    showForecastError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
                loadAlerts();
            }

            @Override
            public void onFailure(@NonNull Call<List<ForecastDto>> call, @NonNull Throwable t) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                showForecastError(getString(R.string.error_no_connection));
                loadAlerts();
            }
        });
    }

    private void loadAlerts() {
        alertCall = api.alerts();
        alertCall.enqueue(new Callback<List<AlertDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<AlertDto>> call, @NonNull Response<List<AlertDto>> response) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                setBusy(false);
                if (SessionNav.expired(InsightsActivity.this, response)) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    showAlerts(response.body());
                } else {
                    showAlertError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<AlertDto>> call, @NonNull Throwable t) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                setBusy(false);
                showAlertError(getString(R.string.error_no_connection));
            }
        });
    }

    private void runForecast() {
        if (forecastCall != null) {
            forecastCall.cancel();
        }
        setBusy(true);
        binding.runButton.setText(R.string.insights_running);
        binding.forecastError.setVisibility(View.GONE);
        forecastCall = api.runForecast();
        forecastCall.enqueue(new Callback<List<ForecastDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<ForecastDto>> call, @NonNull Response<List<ForecastDto>> response) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                setBusy(false);
                binding.runButton.setText(R.string.insights_run);
                if (SessionNav.expired(InsightsActivity.this, response)) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    showForecasts(response.body());
                    Toast.makeText(InsightsActivity.this, R.string.insights_done, Toast.LENGTH_SHORT).show();
                } else {
                    showForecastError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<ForecastDto>> call, @NonNull Throwable t) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                setBusy(false);
                binding.runButton.setText(R.string.insights_run);
                showForecastError(getString(R.string.error_no_connection));
            }
        });
    }

    private void showForecasts(List<ForecastDto> runs) {
        binding.forecastContainer.removeAllViews();
        binding.forecastEmpty.setVisibility(runs.isEmpty() ? View.VISIBLE : View.GONE);
        for (ForecastDto run : runs) {
            ItemInsightBinding card = ItemInsightBinding.inflate(getLayoutInflater(), binding.forecastContainer, true);
            card.titleText.setText(methodLabel(run.method));
            card.bodyText.setText(getString(R.string.insights_history, run.historyStart, run.historyEnd));
            card.bodyText.setVisibility(View.VISIBLE);
            if (run.values != null) {
                for (ForecastDto.ForecastLineDto line : run.values) {
                    ItemMetricRowBinding row = ItemMetricRowBinding.inflate(getLayoutInflater(), card.lineContainer, true);
                    row.metricLabel.setText(line.displayName);
                    row.metricValue.setText(money(line.baselineAmount));
                }
            }
            if (run.warnings != null) {
                for (String warning : run.warnings) {
                    ItemMetricRowBinding row = ItemMetricRowBinding.inflate(getLayoutInflater(), card.lineContainer, true);
                    row.metricLabel.setText(warning);
                    row.metricValue.setText("");
                }
            }
        }
    }

    private void showAlerts(List<AlertDto> alerts) {
        binding.alertContainer.removeAllViews();
        binding.alertEmpty.setVisibility(alerts.isEmpty() ? View.VISIBLE : View.GONE);
        for (AlertDto alert : alerts) {
            ItemWarningBinding card = ItemWarningBinding.inflate(getLayoutInflater(), binding.alertContainer, true);
            String severity = alert.severity == null ? "" : alert.severity;
            card.severityText.setText(severity);
            String status = alert.resolutionStatus == null ? "" : alert.resolutionStatus;
            card.messageText.setText(status.isEmpty() ? alert.message : alert.message + "\n" + status);
            if ("critical".equals(severity)) {
                card.severityText.setBackgroundResource(R.drawable.bg_status_rejected);
            } else if ("info".equals(severity)) {
                card.severityText.setBackgroundResource(R.drawable.bg_callout);
            }
        }
    }

    private String methodLabel(String method) {
        if ("moving_average".equals(method)) {
            return getString(R.string.insights_method_ma);
        }
        if ("exponential_smoothing".equals(method)) {
            return getString(R.string.insights_method_es);
        }
        return method == null ? "" : method;
    }

    private String money(String raw) {
        try {
            return "R " + amount.format(new BigDecimal(raw));
        } catch (Exception ex) {
            return raw == null ? "" : raw;
        }
    }

    private void showForecastError(String message) {
        binding.forecastError.setText(message);
        binding.forecastError.setVisibility(View.VISIBLE);
        binding.forecastEmpty.setVisibility(View.GONE);
    }

    private void showAlertError(String message) {
        binding.alertError.setText(message);
        binding.alertError.setVisibility(View.VISIBLE);
        binding.alertEmpty.setVisibility(View.GONE);
    }

    private void setBusy(boolean busy) {
        binding.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.content.setVisibility(busy ? View.GONE : View.VISIBLE);
        binding.runButton.setEnabled(!busy);
    }

    @Override
    protected void onDestroy() {
        if (forecastCall != null) {
            forecastCall.cancel();
        }
        if (alertCall != null) {
            alertCall.cancel();
        }
        super.onDestroy();
    }
}
