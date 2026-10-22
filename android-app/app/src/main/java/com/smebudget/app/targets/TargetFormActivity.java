package com.smebudget.app.targets;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
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
import com.smebudget.app.submissions.CategoryDto;
import com.smebudget.app.submissions.SubmissionsApi;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Create or edit one KPI target (Boitu). The decision maker role is checked here and again by the API.
 */
public class TargetFormActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "targetId";

    /** Names the meeting report already compares. Gross profit margin must stay these exact words. */
    private static final String MARGIN = "Gross profit margin";
    private static final String[] LEAD_CODES = {"revenue", "electricity", "employee_costs"};

    private ActivityTargetFormBinding binding;
    private TargetsApi api;
    private SubmissionsApi categoriesApi;
    private String targetId;
    private Call<TargetDto> loadCall;
    private Call<List<CategoryDto>> categoriesCall;
    private Call<TargetDto> saveCall;
    private Call<Void> deleteCall;
    private final List<CategoryDto> categories = new ArrayList<>();
    private boolean filling;
    private boolean categoriesFinished;
    private boolean targetFinished;
    private TargetDto loaded;

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
        categoriesApi = ApiClient.create(this, SubmissionsApi.class);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.saveButton.setOnClickListener(v -> save());
        binding.metricInput.setOnItemClickListener((parent, view, position, id) -> {
            if (filling) {
                return;
            }
            Object item = parent.getItemAtPosition(position);
            String name = item == null ? "" : item.toString();
            binding.unitInput.setText(MARGIN.equalsIgnoreCase(name) ? "percent" : "ZAR", false);
        });

        targetId = getIntent().getStringExtra(EXTRA_ID);
        if (targetId != null) {
            binding.toolbar.setTitle(R.string.target_edit);
            binding.deleteButton.setVisibility(View.VISIBLE);
            binding.deleteButton.setOnClickListener(v -> confirmDelete());
            load();
        } else {
            targetFinished = true;
        }
        loadCategories();
    }

    private void loadCategories() {
        setBusy(true);
        categoriesCall = categoriesApi.categories();
        categoriesCall.enqueue(new Callback<List<CategoryDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<CategoryDto>> call, @NonNull Response<List<CategoryDto>> response) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                categoriesFinished = true;
                if (SessionNav.expired(TargetFormActivity.this, response)) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    categories.clear();
                    categories.addAll(response.body());
                } else {
                    showError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
                showForm();
            }

            @Override
            public void onFailure(@NonNull Call<List<CategoryDto>> call, @NonNull Throwable t) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                categoriesFinished = true;
                showError(getString(R.string.error_no_connection));
                showForm();
            }
        });
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
                if (SessionNav.expired(TargetFormActivity.this, response)) {
                    return;
                }
                targetFinished = true;
                if (response.isSuccessful() && response.body() != null) {
                    loaded = response.body();
                } else {
                    showError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
                showForm();
            }

            @Override
            public void onFailure(@NonNull Call<TargetDto> call, @NonNull Throwable t) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                targetFinished = true;
                setBusy(false);
                showError(getString(R.string.error_no_connection));
                showForm();
            }
        });
    }

    /** Fills the name and unit lists once both the categories and, when editing, the target have arrived. */
    private void showForm() {
        if (!categoriesFinished || !targetFinished || isFinishing()) {
            return;
        }
        setBusy(false);
        String metric = loaded != null ? loaded.metricName : (targetId == null ? "Revenue" : "");
        String unit = loaded != null ? loaded.measurementUnit : "ZAR";
        bindChoices(metric, unit);
        if (loaded != null) {
            binding.valueInput.setText(loaded.targetValue == null ? "" : loaded.targetValue.toPlainString());
            binding.periodInput.setText(loaded.targetPeriod);
        }
    }

    private void bindChoices(String selectedMetric, String selectedUnit) {
        filling = true;
        List<String> names = metricNames(selectedMetric);
        ArrayAdapter<String> metrics = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        binding.metricInput.setAdapter(metrics);
        if (selectedMetric != null && selectedMetric.length() > 0) {
            binding.metricInput.setText(selectedMetric, false);
        }
        List<String> units = new ArrayList<>();
        units.add("ZAR");
        units.add("percent");
        if (selectedUnit != null && selectedUnit.length() > 0 && !units.contains(selectedUnit)) {
            units.add(selectedUnit);
        }
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, units);
        binding.unitInput.setAdapter(unitAdapter);
        binding.unitInput.setText(selectedUnit == null || selectedUnit.length() == 0 ? "ZAR" : selectedUnit, false);
        filling = false;
    }

    /**
     * Revenue, Electricity and Employee costs first, then Gross profit margin, then the other
     * line categories. A saved name that is not in that list stays selectable.
     */
    private List<String> metricNames(String current) {
        List<String> names = new ArrayList<>();
        for (String code : LEAD_CODES) {
            String display = displayName(code);
            if (display != null && !names.contains(display)) {
                names.add(display);
            }
        }
        if (!names.contains(MARGIN)) {
            names.add(getString(R.string.target_margin));
        }
        for (CategoryDto category : categories) {
            if (category.displayName == null || names.contains(category.displayName)) {
                continue;
            }
            if ("revenue".equals(category.categoryCode) || "electricity".equals(category.categoryCode)
                    || "employee_costs".equals(category.categoryCode)) {
                continue;
            }
            names.add(category.displayName);
        }
        if (names.isEmpty()) {
            names.add("Revenue");
            names.add("Electricity");
            names.add("Employee costs");
            names.add(MARGIN);
        }
        if (current != null && current.length() > 0 && !names.contains(current)) {
            names.add(current);
        }
        return names;
    }

    private String displayName(String code) {
        for (CategoryDto category : categories) {
            if (code.equals(category.categoryCode) && category.displayName != null) {
                return category.displayName;
            }
        }
        if (categories.isEmpty()) {
            if ("revenue".equals(code)) {
                return "Revenue";
            }
            if ("electricity".equals(code)) {
                return "Electricity";
            }
            if ("employee_costs".equals(code)) {
                return "Employee costs";
            }
        }
        return null;
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
        if (categoriesCall != null) {
            categoriesCall.cancel();
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
