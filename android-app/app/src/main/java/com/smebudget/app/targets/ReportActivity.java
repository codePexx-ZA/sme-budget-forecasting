package com.smebudget.app.targets;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.smebudget.app.R;
import com.smebudget.app.auth.SessionManager;
import com.smebudget.app.databinding.ActivityReportBinding;
import com.smebudget.app.databinding.ItemApprovalBinding;
import com.smebudget.app.databinding.ItemFactBinding;
import com.smebudget.app.databinding.ItemMetricRowBinding;
import com.smebudget.app.databinding.ItemReportRowBinding;
import com.smebudget.app.databinding.ItemWarningBinding;
import com.smebudget.app.network.ApiClient;
import com.smebudget.app.network.ApiErrors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Meeting report (Boitu). One screen of the approved year, both forecasts, targets and warnings.
 * Email uses the phone's own share sheet, so this app never sees a mail password.
 */
public class ReportActivity extends AppCompatActivity {

    private ActivityReportBinding binding;
    private Call<MeetingReportDto> reportCall;
    private String shareText;
    private String businessName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!new SessionManager(this).isLoggedIn()) {
            finish();
            return;
        }
        binding = ActivityReportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.shareButton.setOnClickListener(v -> share());
        load();
    }

    private void load() {
        binding.progress.setVisibility(View.VISIBLE);
        binding.content.setVisibility(View.GONE);
        binding.errorText.setVisibility(View.GONE);
        reportCall = ApiClient.create(this, TargetsApi.class).meetingReport();
        reportCall.enqueue(new Callback<MeetingReportDto>() {
            @Override
            public void onResponse(@NonNull Call<MeetingReportDto> call, @NonNull Response<MeetingReportDto> response) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                binding.progress.setVisibility(View.GONE);
                if (SessionNav.expired(ReportActivity.this, response)) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    show(response.body());
                } else {
                    showError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<MeetingReportDto> call, @NonNull Throwable t) {
                if (call.isCanceled() || isFinishing()) {
                    return;
                }
                binding.progress.setVisibility(View.GONE);
                showError(getString(R.string.error_no_connection));
            }
        });
    }

    private void show(MeetingReportDto report) {
        binding.content.setVisibility(View.VISIBLE);
        binding.shareButton.setEnabled(report.shareText != null && !report.shareText.isEmpty());
        shareText = report.shareText;
        businessName = report.businessName;
        binding.businessName.setText(report.businessName);
        binding.unitName.setText(report.unitName);
        binding.preparedText.setText(getString(R.string.report_prepared, report.preparedFor, report.preparedAt));
        binding.historyNote.setText(report.historyNote);
        binding.forecastNote.setText(report.forecastNote);
        if (report.excludedNote == null || report.excludedNote.isEmpty()) {
            binding.excludedNote.setVisibility(View.GONE);
        } else {
            binding.excludedNote.setText(report.excludedNote);
            binding.excludedNote.setVisibility(View.VISIBLE);
        }
        fillResults(report.results);
        fillRows(report.rows);
        fillTargets(report.targets);
        fillWarnings(report.warnings);
        fillApprovals(report.approvals);
    }

    private void fillResults(java.util.List<MeetingReportDto.Fact> results) {
        binding.resultContainer.removeAllViews();
        if (results == null) {
            return;
        }
        for (MeetingReportDto.Fact fact : results) {
            ItemFactBinding card = ItemFactBinding.inflate(getLayoutInflater(), binding.resultContainer, true);
            card.factLabel.setText(fact.label);
            card.factValue.setText(fact.value);
            if (fact.note == null || fact.note.isEmpty()) {
                card.factNote.setVisibility(View.GONE);
            } else {
                card.factNote.setText(fact.note);
                card.factNote.setVisibility(View.VISIBLE);
            }
        }
    }

    private void fillRows(java.util.List<MeetingReportDto.ComparisonRow> rows) {
        binding.rowContainer.removeAllViews();
        if (rows == null) {
            return;
        }
        for (MeetingReportDto.ComparisonRow row : rows) {
            ItemReportRowBinding card = ItemReportRowBinding.inflate(getLayoutInflater(), binding.rowContainer, true);
            card.categoryText.setText(row.category);
            addMetric(card.metricContainer, getString(R.string.report_column_last), row.lastYear, null);
            addMetric(card.metricContainer, getString(R.string.report_column_ma), row.movingAverage, row.movingAverageTone);
            addMetric(card.metricContainer, getString(R.string.report_column_es), row.exponentialSmoothing, row.exponentialSmoothingTone);
        }
    }

    private void fillTargets(java.util.List<MeetingReportDto.TargetLine> targets) {
        binding.targetContainer.removeAllViews();
        boolean empty = targets == null || targets.isEmpty();
        binding.targetsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            return;
        }
        for (MeetingReportDto.TargetLine target : targets) {
            ItemReportRowBinding card = ItemReportRowBinding.inflate(getLayoutInflater(), binding.targetContainer, true);
            card.categoryText.setText(target.name);
            addMetric(card.metricContainer, getString(R.string.report_figure_target), target.value, null);
            addMetric(card.metricContainer, getString(R.string.target_period), getString(R.string.report_target_period, target.period), null);
            if (target.lastYear != null) {
                addMetric(card.metricContainer, getString(R.string.report_column_last), target.lastYear, null);
                addMetric(card.metricContainer, getString(R.string.report_column_ma), target.movingAverage, target.movingAverageTone);
                addMetric(card.metricContainer, getString(R.string.report_column_es), target.exponentialSmoothing, target.exponentialSmoothingTone);
            }
        }
    }

    private void fillWarnings(java.util.List<MeetingReportDto.WarningLine> warnings) {
        binding.warningContainer.removeAllViews();
        boolean empty = warnings == null || warnings.isEmpty();
        binding.warningsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            return;
        }
        for (MeetingReportDto.WarningLine warning : warnings) {
            ItemWarningBinding card = ItemWarningBinding.inflate(getLayoutInflater(), binding.warningContainer, true);
            String severity = warning.severity == null ? "" : warning.severity;
            card.severityText.setText(severity);
            card.messageText.setText(warning.message);
            if ("critical".equals(severity)) {
                card.severityText.setBackgroundResource(R.drawable.bg_status_rejected);
            } else if ("info".equals(severity)) {
                card.severityText.setBackgroundResource(R.drawable.bg_callout);
            }
        }
    }

    private void fillApprovals(java.util.List<MeetingReportDto.ApprovalNote> approvals) {
        binding.approvalContainer.removeAllViews();
        boolean empty = approvals == null || approvals.isEmpty();
        binding.approvalsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            return;
        }
        for (MeetingReportDto.ApprovalNote note : approvals) {
            ItemApprovalBinding card = ItemApprovalBinding.inflate(getLayoutInflater(), binding.approvalContainer, true);
            card.approverText.setText(note.approver);
            if (note.when == null || note.when.isEmpty()) {
                card.whenText.setVisibility(View.GONE);
            } else {
                card.whenText.setText(getString(R.string.report_approved_on, note.when));
            }
            card.periodText.setText(getString(R.string.report_covers, note.period));
            if (note.comment == null || note.comment.isEmpty()) {
                card.commentText.setVisibility(View.GONE);
            } else {
                card.commentText.setText(note.comment);
                card.commentText.setVisibility(View.VISIBLE);
            }
        }
    }

    private void addMetric(android.widget.LinearLayout parent, String label, String value, String tone) {
        ItemMetricRowBinding row = ItemMetricRowBinding.inflate(getLayoutInflater(), parent, true);
        row.metricLabel.setText(label);
        row.metricValue.setText(value == null ? "" : value);
        if ("favorable".equals(tone)) {
            box(row.metricValue, R.drawable.bg_tone_favorable, R.color.status_approved_fg);
        } else if ("unfavorable".equals(tone)) {
            box(row.metricValue, R.drawable.bg_tone_unfavorable, R.color.status_rejected_fg);
        }
    }

    /** A small coloured box around a forecast that beat or missed last year. */
    private void box(android.widget.TextView value, int background, int textColor) {
        int padH = getResources().getDimensionPixelSize(R.dimen.space_8);
        int padV = getResources().getDimensionPixelSize(R.dimen.space_4);
        value.setBackgroundResource(background);
        value.setTextColor(ContextCompat.getColor(this, textColor));
        value.setPadding(padH, padV, padH, padV);
    }

    private void share() {
        if (shareText == null || shareText.isEmpty()) {
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "Budget report - " + (businessName == null ? "" : businessName));
        send.putExtra(Intent.EXTRA_TEXT, shareText);
        try {
            startActivity(Intent.createChooser(send, getString(R.string.report_share_chooser)));
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(this, R.string.report_share_missing, Toast.LENGTH_LONG).show();
        }
    }

    private void showError(String message) {
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
        binding.shareButton.setEnabled(false);
    }

    @Override
    protected void onDestroy() {
        if (reportCall != null) {
            reportCall.cancel();
        }
        super.onDestroy();
    }
}
