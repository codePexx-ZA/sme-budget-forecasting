package com.smebudget.app.submissions;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.smebudget.app.R;
import com.smebudget.app.databinding.ActivitySubmissionFormBinding;
import com.smebudget.app.databinding.ItemLineEditorBinding;
import com.smebudget.app.network.ApiClient;
import com.smebudget.app.network.ApiErrors;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Creates a draft or edits a draft or rejected submission (Aiden).
 * The phone checks the dates and amounts first. The API checks them again.
 */
public class SubmissionFormActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "submission_id";
    private static final String STATE_FORM = "submission_form";

    private ActivitySubmissionFormBinding binding;
    private SubmissionsApi api;
    private String editingId;
    private String pendingSnapshot;
    private boolean formReady;
    private boolean saving;
    private boolean expired;
    private int pendingLoads;
    private String loadError;

    private final List<CategoryDto> categories = new ArrayList<>();
    private final List<SubmissionSummary> summaries = new ArrayList<>();
    private SubmissionDetail loadedDetail;
    private FinancialYearDto financialYear;
    private CodedOption[] categoryOptions = new CodedOption[0];
    private CodedOption[] importOptions;
    private CodedOption[] scaleOptions;
    private CodedOption[] frequencyOptions;
    private CodedOption[] replacesOptions = new CodedOption[0];
    private final List<LineRow> rows = new ArrayList<>();

    private Call<List<CategoryDto>> categoriesCall;
    private Call<List<SubmissionSummary>> listCall;
    private Call<FinancialYearDto> yearCall;
    private Call<SubmissionDetail> detailCall;
    private Call<SubmissionDetail> saveCall;
    private Call<SubmissionDetail> copyCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        editingId = getIntent().getStringExtra(EXTRA_ID);
        if (editingId != null && editingId.trim().length() == 0) {
            editingId = null;
        }
        if (savedInstanceState != null) {
            pendingSnapshot = savedInstanceState.getString(STATE_FORM);
        }

        binding = ActivitySubmissionFormBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        api = ApiClient.create(this, SubmissionsApi.class);
        binding.toolbar.setTitle(editingId == null ? R.string.submission_new : R.string.submission_edit);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        importOptions = new CodedOption[] {
                new CodedOption("manual", getString(R.string.import_manual)),
                new CodedOption("template", getString(R.string.import_template)),
                new CodedOption("mapped_import", getString(R.string.import_mapped))
        };
        scaleOptions = new CodedOption[] {
                new CodedOption("1", getString(R.string.scale_rands)),
                new CodedOption("1000", getString(R.string.scale_thousands)),
                new CodedOption("1000000", getString(R.string.scale_millions))
        };
        frequencyOptions = new CodedOption[] {
                new CodedOption("annual", getString(R.string.frequency_annual)),
                new CodedOption("quarterly", getString(R.string.frequency_quarterly)),
                new CodedOption("monthly", getString(R.string.frequency_monthly))
        };

        binding.periodStartInput.setOnClickListener(v ->
                pickDate(binding.periodStartInput, binding.periodStartLayout));
        binding.periodEndInput.setOnClickListener(v ->
                pickDate(binding.periodEndInput, binding.periodEndLayout));
        binding.thisYearButton.setOnClickListener(v -> applyFinancialYear(false));
        binding.nextYearButton.setOnClickListener(v -> applyFinancialYear(true));
        binding.copyLinesButton.setOnClickListener(v -> chooseCopySource());
        binding.addLineButton.setOnClickListener(v -> addLine(null, null, null, "annual"));
        binding.saveButton.setOnClickListener(v -> save());
        load();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (formReady) {
            outState.putString(STATE_FORM, new Gson().toJson(snapshot()));
        } else if (pendingSnapshot != null) {
            outState.putString(STATE_FORM, pendingSnapshot);
        }
    }

    /** Categories and the replaces list have to arrive before the form can be filled in. */
    private void load() {
        setFormVisible(false);
        binding.progress.setVisibility(View.VISIBLE);
        binding.loadErrorText.setVisibility(View.GONE);
        boolean needDetail = editingId != null && pendingSnapshot == null;
        pendingLoads = needDetail ? 4 : 3;
        loadError = null;
        expired = false;

        categoriesCall = api.categories();
        categoriesCall.enqueue(new Callback<List<CategoryDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<CategoryDto>> call, @NonNull Response<List<CategoryDto>> response) {
                if (stopped(call)) {
                    return;
                }
                if (SessionNav.expired(SubmissionFormActivity.this, response)) {
                    expired = true;
                    finishLoad();
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    categories.clear();
                    categories.addAll(response.body());
                } else if (loadError == null) {
                    loadError = ApiErrors.message(response, getString(R.string.error_unexpected));
                }
                finishLoad();
            }

            @Override
            public void onFailure(@NonNull Call<List<CategoryDto>> call, @NonNull Throwable t) {
                if (stopped(call)) {
                    return;
                }
                if (loadError == null) {
                    loadError = getString(R.string.error_no_connection);
                }
                finishLoad();
            }
        });

        listCall = api.list();
        listCall.enqueue(new Callback<List<SubmissionSummary>>() {
            @Override
            public void onResponse(@NonNull Call<List<SubmissionSummary>> call,
                                   @NonNull Response<List<SubmissionSummary>> response) {
                if (stopped(call)) {
                    return;
                }
                if (SessionNav.expired(SubmissionFormActivity.this, response)) {
                    expired = true;
                    finishLoad();
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    summaries.clear();
                    summaries.addAll(response.body());
                } else if (loadError == null) {
                    loadError = ApiErrors.message(response, getString(R.string.error_unexpected));
                }
                finishLoad();
            }

            @Override
            public void onFailure(@NonNull Call<List<SubmissionSummary>> call, @NonNull Throwable t) {
                if (stopped(call)) {
                    return;
                }
                if (loadError == null) {
                    loadError = getString(R.string.error_no_connection);
                }
                finishLoad();
            }
        });

        yearCall = api.financialYear();
        yearCall.enqueue(new Callback<FinancialYearDto>() {
            @Override
            public void onResponse(@NonNull Call<FinancialYearDto> call, @NonNull Response<FinancialYearDto> response) {
                if (stopped(call)) {
                    return;
                }
                if (SessionNav.expired(SubmissionFormActivity.this, response)) {
                    expired = true;
                    finishLoad();
                    return;
                }
                // A missing year does not block the draft. The chips explain that the dates were not filled.
                if (response.isSuccessful()) {
                    financialYear = response.body();
                }
                finishLoad();
            }

            @Override
            public void onFailure(@NonNull Call<FinancialYearDto> call, @NonNull Throwable t) {
                if (stopped(call)) {
                    return;
                }
                finishLoad();
            }
        });

        if (needDetail) {
            detailCall = api.get(editingId);
            detailCall.enqueue(new Callback<SubmissionDetail>() {
                @Override
                public void onResponse(@NonNull Call<SubmissionDetail> call, @NonNull Response<SubmissionDetail> response) {
                    if (stopped(call)) {
                        return;
                    }
                    if (SessionNav.expired(SubmissionFormActivity.this, response)) {
                        expired = true;
                        finishLoad();
                        return;
                    }
                    if (response.isSuccessful() && response.body() != null) {
                        loadedDetail = response.body();
                    } else if (loadError == null) {
                        loadError = ApiErrors.message(response, getString(R.string.error_unexpected));
                    }
                    finishLoad();
                }

                @Override
                public void onFailure(@NonNull Call<SubmissionDetail> call, @NonNull Throwable t) {
                    if (stopped(call)) {
                        return;
                    }
                    if (loadError == null) {
                        loadError = getString(R.string.error_no_connection);
                    }
                    finishLoad();
                }
            });
        }
    }

    private void finishLoad() {
        pendingLoads--;
        if (pendingLoads > 0 || expired || isFinishing() || isDestroyed()) {
            return;
        }
        binding.progress.setVisibility(View.GONE);
        if (loadError != null) {
            binding.loadErrorText.setText(loadError);
            binding.loadErrorText.setVisibility(View.VISIBLE);
            return;
        }
        if (categories.isEmpty()) {
            binding.loadErrorText.setText(R.string.error_unexpected);
            binding.loadErrorText.setVisibility(View.VISIBLE);
            return;
        }
        categoryOptions = new CodedOption[categories.size()];
        for (int i = 0; i < categories.size(); i++) {
            CategoryDto category = categories.get(i);
            categoryOptions[i] = new CodedOption(category.categoryCode, category.displayName);
        }
        replacesOptions = buildReplacesOptions(null);
        bindOptions(binding.importMethodInput, importOptions, "manual");
        bindOptions(binding.sourceScaleInput, scaleOptions, "1");
        bindOptions(binding.replacesInput, replacesOptions, null);

        if (pendingSnapshot != null) {
            applySnapshot(new Gson().fromJson(pendingSnapshot, FormSnapshot.class));
            pendingSnapshot = null;
        } else if (loadedDetail != null) {
            fillDetail(loadedDetail);
        } else {
            addLine(null, null, null, "annual");
        }
        setFormVisible(true);
        formReady = true;
    }

    private CodedOption[] buildReplacesOptions(String extraId) {
        List<CodedOption> options = new ArrayList<>();
        options.add(new CodedOption(null, getString(R.string.replaces_none)));
        boolean foundExtra = extraId == null;
        for (SubmissionSummary summary : summaries) {
            if (summary.submissionId == null || summary.submissionId.equals(editingId)) {
                continue;
            }
            if (summary.submissionId.equals(extraId)) {
                foundExtra = true;
            }
            String label = getString(R.string.period_range,
                    SubmissionText.prettyDate(summary.periodStart),
                    SubmissionText.prettyDate(summary.periodEnd))
                    + " · " + SubmissionText.status(this, summary.status);
            options.add(new CodedOption(summary.submissionId, label));
        }
        if (!foundExtra) {
            String label = extraId;
            if (loadedDetail != null && extraId.equals(loadedDetail.replacesSubmissionId) && loadedDetail.replacesLabel != null) {
                label = loadedDetail.replacesLabel;
            }
            options.add(new CodedOption(extraId, label));
        }
        return options.toArray(new CodedOption[0]);
    }

    private void fillDetail(SubmissionDetail detail) {
        setDate(binding.periodStartInput, detail.periodStart);
        setDate(binding.periodEndInput, detail.periodEnd);
        bindOptions(binding.importMethodInput, importOptions, detail.importMethod);
        String scale = detail.sourceScale == null ? "1" : String.valueOf(detail.sourceScale);
        bindOptions(binding.sourceScaleInput, scaleOptions, scale);
        binding.fileNameInput.setText(detail.sourceFileName == null ? "" : detail.sourceFileName);
        if (detail.replacesSubmissionId != null) {
            replacesOptions = buildReplacesOptions(detail.replacesSubmissionId);
            bindOptions(binding.replacesInput, replacesOptions, detail.replacesSubmissionId);
        }
        clearLines();
        if (detail.lineItems != null) {
            for (LineItemDto line : detail.lineItems) {
                String amount = line.amount == null ? "" : line.amount.toPlainString();
                addLine(line.category, line.periodStart, amount, line.frequency);
            }
        }
    }

    private void applySnapshot(FormSnapshot snapshot) {
        if (snapshot == null) {
            addLine(null, null, null, "annual");
            return;
        }
        setDate(binding.periodStartInput, snapshot.periodStart);
        setDate(binding.periodEndInput, snapshot.periodEnd);
        bindOptions(binding.importMethodInput, importOptions, snapshot.importMethod);
        bindOptions(binding.sourceScaleInput, scaleOptions, snapshot.sourceScale);
        binding.fileNameInput.setText(snapshot.fileName == null ? "" : snapshot.fileName);
        if (snapshot.replacesId != null) {
            replacesOptions = buildReplacesOptions(snapshot.replacesId);
        }
        bindOptions(binding.replacesInput, replacesOptions, snapshot.replacesId);
        clearLines();
        if (snapshot.lines != null) {
            for (LineSnapshot line : snapshot.lines) {
                addLine(line.categoryCode, line.periodStart, line.amount, line.frequency);
            }
        }
    }

    private FormSnapshot snapshot() {
        FormSnapshot snapshot = new FormSnapshot();
        snapshot.periodStart = isoOf(binding.periodStartInput);
        snapshot.periodEnd = isoOf(binding.periodEndInput);
        snapshot.importMethod = codeOf(binding.importMethodInput, importOptions);
        snapshot.sourceScale = codeOf(binding.sourceScaleInput, scaleOptions);
        snapshot.fileName = textOf(binding.fileNameInput);
        snapshot.replacesId = codeOf(binding.replacesInput, replacesOptions);
        snapshot.lines = new ArrayList<>();
        for (LineRow row : rows) {
            LineSnapshot line = new LineSnapshot();
            line.categoryCode = codeOf(row.binding.categoryInput, categoryOptions);
            line.periodStart = isoOf(row.binding.linePeriodInput);
            line.amount = textOf(row.binding.amountInput);
            line.frequency = codeOf(row.binding.frequencyInput, frequencyOptions);
            snapshot.lines.add(line);
        }
        return snapshot;
    }

    private void save() {
        if (saving) {
            return;
        }
        SubmissionBody body = buildBody();
        if (body == null) {
            return;
        }
        setSaving(true);
        Call<SubmissionDetail> call = editingId == null ? api.create(body) : api.update(editingId, body);
        saveCall = call;
        call.enqueue(new Callback<SubmissionDetail>() {
            @Override
            public void onResponse(@NonNull Call<SubmissionDetail> responseCall, @NonNull Response<SubmissionDetail> response) {
                if (stopped(responseCall)) {
                    return;
                }
                setSaving(false);
                if (SessionNav.expired(SubmissionFormActivity.this, response)) {
                    return;
                }
                if (response.isSuccessful()) {
                    Toast.makeText(SubmissionFormActivity.this, R.string.budget_saved, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    showError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<SubmissionDetail> responseCall, @NonNull Throwable t) {
                if (stopped(responseCall)) {
                    return;
                }
                setSaving(false);
                showError(getString(R.string.error_no_connection));
            }
        });
    }

    /** Checks the form on the phone. Returns null and shows the first problem when something is wrong. */
    private SubmissionBody buildBody() {
        binding.periodStartLayout.setError(null);
        binding.periodEndLayout.setError(null);
        binding.errorText.setVisibility(View.GONE);
        String start = isoOf(binding.periodStartInput);
        String end = isoOf(binding.periodEndInput);
        if (start == null || end == null) {
            if (start == null) {
                binding.periodStartLayout.setError(getString(R.string.period_start));
            }
            if (end == null) {
                binding.periodEndLayout.setError(getString(R.string.period_end));
            }
            showError(getString(R.string.error_period_required));
            return null;
        }
        if (start.compareTo(end) > 0) {
            binding.periodEndLayout.setError(getString(R.string.error_period_order));
            showError(getString(R.string.error_period_order));
            return null;
        }

        List<LineItemBody> lines = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (LineRow row : rows) {
            row.binding.categoryLayout.setError(null);
            row.binding.linePeriodLayout.setError(null);
            row.binding.amountLayout.setError(null);
            String category = codeOf(row.binding.categoryInput, categoryOptions);
            if (category == null || category.length() == 0) {
                row.binding.categoryLayout.setError(getString(R.string.error_category));
                showError(getString(R.string.error_category));
                return null;
            }
            String linePeriod = isoOf(row.binding.linePeriodInput);
            if (linePeriod == null) {
                row.binding.linePeriodLayout.setError(getString(R.string.error_line_period));
                showError(getString(R.string.error_line_period));
                return null;
            }
            if (linePeriod.compareTo(start) < 0 || linePeriod.compareTo(end) > 0) {
                row.binding.linePeriodLayout.setError(getString(R.string.error_line_outside));
                showError(getString(R.string.error_line_outside));
                return null;
            }
            BigDecimal amount = SubmissionText.parseAmount(textOf(row.binding.amountInput));
            if (amount == null) {
                row.binding.amountLayout.setError(getString(R.string.error_line_amount));
                showError(getString(R.string.error_line_amount));
                return null;
            }
            if (!seen.add(category + "|" + linePeriod)) {
                row.binding.categoryLayout.setError(getString(R.string.error_duplicate_line));
                showError(getString(R.string.error_duplicate_line));
                return null;
            }
            String frequency = codeOf(row.binding.frequencyInput, frequencyOptions);
            lines.add(new LineItemBody(category, linePeriod, amount, frequency == null ? "annual" : frequency));
        }

        String fileName = textOf(binding.fileNameInput).trim();
        String scaleCode = codeOf(binding.sourceScaleInput, scaleOptions);
        int scale = 1;
        if (scaleCode != null) {
            try {
                scale = Integer.parseInt(scaleCode);
            } catch (NumberFormatException ex) {
                scale = 1;
            }
        }
        return new SubmissionBody(
                start, end, codeOf(binding.importMethodInput, importOptions),
                fileName.length() == 0 ? null : fileName, scale,
                codeOf(binding.replacesInput, replacesOptions), lines);
    }

    private void addLine(String categoryCode, String periodIso, String amount, String frequencyCode) {
        ItemLineEditorBinding rowBinding = ItemLineEditorBinding.inflate(getLayoutInflater(), binding.linesContainer, true);
        LineRow row = new LineRow(rowBinding);
        rows.add(row);
        String category = categoryCode;
        if (category == null && categoryOptions.length > 0) {
            category = categoryOptions[0].code;
        }
        bindOptions(rowBinding.categoryInput, categoryOptions, category);
        bindOptions(rowBinding.frequencyInput, frequencyOptions, frequencyCode == null ? "annual" : frequencyCode);
        if (periodIso != null) {
            setDate(rowBinding.linePeriodInput, periodIso);
        }
        if (amount != null) {
            rowBinding.amountInput.setText(amount);
        }
        rowBinding.linePeriodInput.setOnClickListener(v -> pickDate(rowBinding.linePeriodInput, rowBinding.linePeriodLayout));
        rowBinding.removeButton.setOnClickListener(v -> {
            binding.linesContainer.removeView(rowBinding.getRoot());
            rows.remove(row);
        });
    }

    /**
     * Fills the two period fields from the business year-end. Dates the submitter already
     * typed are replaced. Line periods that would fall outside the new range move to the start,
     * so a copied year can be saved without editing every row.
     */
    private void applyFinancialYear(boolean next) {
        if (financialYear == null) {
            Toast.makeText(this, R.string.fy_missing, Toast.LENGTH_LONG).show();
            return;
        }
        String start = next ? financialYear.nextStart : financialYear.thisStart;
        String end = next ? financialYear.nextEnd : financialYear.thisEnd;
        if (start == null || end == null) {
            Toast.makeText(this, R.string.fy_missing, Toast.LENGTH_LONG).show();
            return;
        }
        setDate(binding.periodStartInput, start);
        setDate(binding.periodEndInput, end);
        binding.periodStartLayout.setError(null);
        binding.periodEndLayout.setError(null);
        for (LineRow row : rows) {
            String linePeriod = isoOf(row.binding.linePeriodInput);
            if (linePeriod == null || linePeriod.compareTo(start) < 0 || linePeriod.compareTo(end) > 0) {
                setDate(row.binding.linePeriodInput, start);
                row.binding.linePeriodLayout.setError(null);
            }
        }
    }

    /** Approved budgets only. The detail call is the same one the form uses when editing. */
    private void chooseCopySource() {
        List<SubmissionSummary> approved = new ArrayList<>();
        for (SubmissionSummary summary : summaries) {
            if (summary.submissionId == null || summary.submissionId.equals(editingId)) {
                continue;
            }
            if (!"approved".equals(summary.status)) {
                continue;
            }
            approved.add(summary);
        }
        if (approved.isEmpty()) {
            Toast.makeText(this, R.string.copy_lines_empty, Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[approved.size()];
        for (int i = 0; i < approved.size(); i++) {
            SubmissionSummary summary = approved.get(i);
            labels[i] = getString(R.string.period_range,
                    SubmissionText.prettyDate(summary.periodStart),
                    SubmissionText.prettyDate(summary.periodEnd));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.copy_lines_title)
                .setItems(labels, (dialog, which) -> confirmCopy(approved.get(which).submissionId))
                .show();
    }

    private void confirmCopy(String submissionId) {
        if (!linesHaveFigures()) {
            loadCopy(submissionId);
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.copy_lines_replace)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.copy_lines_confirm, (dialog, which) -> loadCopy(submissionId))
                .show();
    }

    private boolean linesHaveFigures() {
        for (LineRow row : rows) {
            if (textOf(row.binding.amountInput).trim().length() > 0) {
                return true;
            }
        }
        return false;
    }

    private void loadCopy(String submissionId) {
        if (copyCall != null) {
            copyCall.cancel();
        }
        setSaving(true);
        copyCall = api.get(submissionId);
        copyCall.enqueue(new Callback<SubmissionDetail>() {
            @Override
            public void onResponse(@NonNull Call<SubmissionDetail> call, @NonNull Response<SubmissionDetail> response) {
                if (stopped(call)) {
                    return;
                }
                setSaving(false);
                if (SessionNav.expired(SubmissionFormActivity.this, response)) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    applyCopiedLines(response.body());
                    Toast.makeText(SubmissionFormActivity.this, R.string.copy_lines_done, Toast.LENGTH_LONG).show();
                } else {
                    showError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<SubmissionDetail> call, @NonNull Throwable t) {
                if (stopped(call)) {
                    return;
                }
                setSaving(false);
                showError(getString(R.string.error_no_connection));
            }
        });
    }

    /** Copies categories, amounts and frequencies, and points replaces at that budget. Dates stay. */
    private void applyCopiedLines(SubmissionDetail detail) {
        replacesOptions = buildReplacesOptions(detail.submissionId);
        bindOptions(binding.replacesInput, replacesOptions, detail.submissionId);
        clearLines();
        if (detail.lineItems != null) {
            for (LineItemDto line : detail.lineItems) {
                String amount = line.amount == null ? "" : line.amount.toPlainString();
                addLine(line.category, line.periodStart, amount, line.frequency);
            }
        }
    }

    /** Drops edited rows and leaves the column headings in place. */
    private void clearLines() {
        for (int i = binding.linesContainer.getChildCount() - 1; i >= 0; i--) {
            if (binding.linesContainer.getChildAt(i).getId() != R.id.lineHeaderRow) {
                binding.linesContainer.removeViewAt(i);
            }
        }
        rows.clear();
    }

    private void pickDate(TextInputEditText target, TextInputLayout layout) {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(layout.getHint() == null ? getString(R.string.choose_date) : layout.getHint())
                .setSelection(initialSelection(target))
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            String iso = isoFromUtcMillis(selection);
            setDate(target, iso);
            layout.setError(null);
        });
        picker.show(getSupportFragmentManager(), "date-" + target.hashCode());
    }

    private static long initialSelection(TextInputEditText target) {
        String iso = isoOf(target);
        if (iso != null) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
                parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                parser.setLenient(false);
                Date date = parser.parse(iso);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ex) {
                // Fall through to today.
            }
        }
        return MaterialDatePicker.todayInUtcMilliseconds();
    }

    private static String isoFromUtcMillis(long millis) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(millis);
        return String.format(Locale.US, "%04d-%02d-%02d",
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH) + 1,
                calendar.get(Calendar.DAY_OF_MONTH));
    }

    private static void setDate(TextInputEditText input, String iso) {
        if (iso == null || iso.length() < 10) {
            input.setTag(null);
            input.setText("");
            return;
        }
        String day = iso.substring(0, 10);
        input.setTag(day);
        input.setText(SubmissionText.prettyDate(day));
    }

    private static String isoOf(TextInputEditText input) {
        Object tag = input.getTag();
        if (tag instanceof String) {
            String iso = (String) tag;
            if (iso.length() >= 10) {
                return iso.substring(0, 10);
            }
        }
        return null;
    }

    private void bindOptions(MaterialAutoCompleteTextView view, CodedOption[] options, String selectedCode) {
        if (options == null || options.length == 0) {
            return;
        }
        ArrayAdapter<CodedOption> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, options);
        view.setAdapter(adapter);
        CodedOption selected = options[0];
        if (selectedCode != null) {
            for (CodedOption option : options) {
                if (selectedCode.equals(option.code)) {
                    selected = option;
                    break;
                }
            }
        }
        view.setText(selected.label, false);
    }

    private static String codeOf(MaterialAutoCompleteTextView view, CodedOption[] options) {
        if (options == null) {
            return null;
        }
        String text = view.getText() == null ? "" : view.getText().toString();
        for (CodedOption option : options) {
            if (option.label.equals(text)) {
                return option.code;
            }
        }
        return null;
    }

    private void setFormVisible(boolean visible) {
        binding.formScroll.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.saveButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setSaving(boolean saving) {
        this.saving = saving;
        binding.progress.setVisibility(saving ? View.VISIBLE : View.GONE);
        binding.saveButton.setEnabled(!saving);
        binding.addLineButton.setEnabled(!saving);
        binding.copyLinesButton.setEnabled(!saving);
        binding.thisYearButton.setEnabled(!saving);
        binding.nextYearButton.setEnabled(!saving);
    }

    private void showError(String message) {
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    private boolean stopped(Call<?> call) {
        return call.isCanceled() || isFinishing() || isDestroyed();
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    @Override
    protected void onDestroy() {
        cancel(categoriesCall);
        cancel(listCall);
        cancel(yearCall);
        cancel(detailCall);
        cancel(saveCall);
        cancel(copyCall);
        super.onDestroy();
    }

    private static void cancel(Call<?> call) {
        if (call != null) {
            call.cancel();
        }
    }

    private static final class LineRow {
        final ItemLineEditorBinding binding;

        LineRow(ItemLineEditorBinding binding) {
            this.binding = binding;
        }
    }

    /** Remembered form contents, so rotating the phone does not wipe what was typed. */
    private static final class FormSnapshot {
        String periodStart;
        String periodEnd;
        String importMethod;
        String sourceScale;
        String fileName;
        String replacesId;
        List<LineSnapshot> lines;
    }

    private static final class LineSnapshot {
        String categoryCode;
        String periodStart;
        String amount;
        String frequency;
    }
}
