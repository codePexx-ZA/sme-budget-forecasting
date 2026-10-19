package com.smebudget.app.submissions;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smebudget.app.R;
import com.smebudget.app.auth.Roles;
import com.smebudget.app.auth.SessionManager;
import com.smebudget.app.databinding.ActivitySubmissionDetailBinding;
import com.smebudget.app.network.ApiClient;
import com.smebudget.app.network.ApiErrors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * One budget submission (Aiden): a summary table, the line items, and the actions the
 * logged-in role is allowed to take.
 */
public class SubmissionDetailActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "submission_id";

    private ActivitySubmissionDetailBinding binding;
    private SessionManager session;
    private SubmissionsApi api;
    private String submissionId;
    private SubmissionDetail current;
    private Call<SubmissionDetail> loadCall;
    private Call<?> actionCall;
    private boolean actionRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        submissionId = getIntent().getStringExtra(EXTRA_ID);
        if (!session.isLoggedIn() || submissionId == null || submissionId.trim().length() == 0) {
            finish();
            return;
        }

        binding = ActivitySubmissionDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        api = ApiClient.create(this, SubmissionsApi.class);
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.editButton.setOnClickListener(v -> openEditor());
        binding.submitButton.setOnClickListener(v -> confirmSubmit());
        binding.approveButton.setOnClickListener(v -> confirmApprove());
        binding.rejectButton.setOnClickListener(v -> confirmReject());
        binding.deleteButton.setOnClickListener(v -> confirmDelete());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!actionRunning) {
            load();
        }
    }

    private void load() {
        if (loadCall != null) {
            loadCall.cancel();
        }
        binding.progress.setVisibility(View.VISIBLE);
        binding.loadErrorText.setVisibility(View.GONE);
        loadCall = api.get(submissionId);
        loadCall.enqueue(new Callback<SubmissionDetail>() {
            @Override
            public void onResponse(@NonNull Call<SubmissionDetail> call, @NonNull Response<SubmissionDetail> response) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) {
                    return;
                }
                binding.progress.setVisibility(View.GONE);
                if (SessionNav.expired(SubmissionDetailActivity.this, response)) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    show(response.body());
                } else {
                    showLoadError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<SubmissionDetail> call, @NonNull Throwable t) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) {
                    return;
                }
                binding.progress.setVisibility(View.GONE);
                showLoadError(getString(R.string.error_no_connection));
            }
        });
    }

    private void show(SubmissionDetail detail) {
        current = detail;
        binding.content.setVisibility(View.VISIBLE);
        binding.loadErrorText.setVisibility(View.GONE);
        binding.errorText.setVisibility(View.GONE);
        binding.toolbar.setTitle(getString(R.string.period_range,
                SubmissionText.prettyDate(detail.periodStart), SubmissionText.prettyDate(detail.periodEnd)));
        binding.toolbar.setSubtitle(R.string.submission_detail_title);
        binding.statusText.setText(SubmissionText.status(this, detail.status));
        StatusChips.apply(binding.statusText, detail.status);
        String meaning = SubmissionText.statusMeaning(this, detail.status);
        binding.statusMeaning.setText(meaning);
        binding.statusMeaning.setVisibility(meaning.length() == 0 ? View.GONE : View.VISIBLE);
        binding.nextStepText.setText(nextStep(detail));
        showSummary(detail);
        showLines(detail);
        showApprovals(detail);
        showActions(detail);
    }

    /** Label and value. Profit stays per period, so several periods are listed and not added together. */
    private void showSummary(SubmissionDetail detail) {
        binding.summaryContainer.removeAllViews();
        TableLayout table = new TableLayout(this);
        table.setStretchAllColumns(true);
        table.setShrinkAllColumns(true);
        table.addView(summaryRow(getString(R.string.section_period), getString(R.string.period_range,
                SubmissionText.prettyDate(detail.periodStart), SubmissionText.prettyDate(detail.periodEnd)), false));
        table.addView(summaryRow(getString(R.string.status_label), SubmissionText.status(this, detail.status), false));
        String submitter = detail.submitterName == null ? "" : detail.submitterName;
        table.addView(summaryRow(getString(R.string.submitted_by_label), submitter, false));
        table.addView(summaryRow(getString(R.string.import_method),
                SubmissionText.importMethod(this, detail.importMethod), false));
        table.addView(summaryRow(getString(R.string.summary_scale),
                SubmissionText.scale(this, detail.sourceScale), false));
        table.addView(summaryRow(getString(R.string.gross_profit), profitValue(detail, 0), true));
        table.addView(summaryRow(getString(R.string.operating_profit), profitValue(detail, 1), true));
        table.addView(summaryRow(getString(R.string.net_profit), profitValue(detail, 2), true));
        binding.summaryContainer.addView(tableFrame(table));
    }

    /** One amount when the budget has one period. Otherwise each period keeps its own figure. */
    private String profitValue(SubmissionDetail detail, int field) {
        List<PeriodTotalsDto> rows = profitRows(detail);
        if (rows.isEmpty()) {
            return "";
        }
        if (rows.size() == 1) {
            return SubmissionText.money(profitAmount(rows.get(0), field));
        }
        StringBuilder text = new StringBuilder();
        for (PeriodTotalsDto row : rows) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(SubmissionText.prettyDate(row.periodStart));
            text.append('\n');
            text.append(SubmissionText.money(profitAmount(row, field)));
        }
        return text.toString();
    }

    private List<PeriodTotalsDto> profitRows(SubmissionDetail detail) {
        List<PeriodTotalsDto> rows = new ArrayList<>();
        if (detail.totals == null) {
            return rows;
        }
        String seen = null;
        if (detail.lineItems != null) {
            for (LineItemDto line : detail.lineItems) {
                if (line.periodStart == null || line.periodStart.equals(seen)) {
                    continue;
                }
                seen = line.periodStart;
                PeriodTotalsDto totals = totalsFor(detail, line.periodStart);
                if (totals != null) {
                    rows.add(totals);
                }
            }
        }
        for (PeriodTotalsDto candidate : detail.totals) {
            boolean already = false;
            for (PeriodTotalsDto row : rows) {
                if (candidate.periodStart != null && candidate.periodStart.equals(row.periodStart)) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                rows.add(candidate);
            }
        }
        return rows;
    }

    private BigDecimal profitAmount(PeriodTotalsDto totals, int field) {
        if (field == 0) {
            return totals.grossProfit;
        }
        if (field == 1) {
            return totals.operatingProfit;
        }
        return totals.netProfit;
    }

    /** Lines stay in period order. Profit figures are in the summary table. */
    private void showLines(SubmissionDetail detail) {
        binding.linesContainer.removeAllViews();
        if (detail.lineItems == null || detail.lineItems.isEmpty()) {
            TextView empty = bodyText();
            empty.setText(R.string.no_line_items);
            binding.linesContainer.addView(empty);
            return;
        }
        TableLayout lines = new TableLayout(this);
        lines.setStretchAllColumns(true);
        lines.setShrinkAllColumns(true);
        lines.addView(tableRow(true, 2,
                getString(R.string.category),
                getString(R.string.section_period),
                getString(R.string.amount),
                getString(R.string.frequency)));
        for (LineItemDto line : detail.lineItems) {
            String name = line.categoryName == null ? line.category : line.categoryName;
            lines.addView(tableRow(false, 2,
                    name,
                    SubmissionText.prettyDate(line.periodStart),
                    SubmissionText.money(line.amount),
                    SubmissionText.frequency(this, line.frequency)));
        }
        binding.linesContainer.addView(tableFrame(lines));
    }

    private PeriodTotalsDto totalsFor(SubmissionDetail detail, String period) {
        for (PeriodTotalsDto candidate : detail.totals) {
            if (period.equals(candidate.periodStart)) {
                return candidate;
            }
        }
        return null;
    }

    /** endFrom is the first column that holds an amount, so those figures line up on the right. */
    private TableRow tableRow(boolean header, int endFrom, String... cells) {
        TableRow row = new TableRow(this);
        row.setBackgroundResource(header ? R.drawable.bg_table_header : R.drawable.bg_table_row);
        row.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        row.setDividerDrawable(getDrawable(R.drawable.divider_table));
        for (int i = 0; i < cells.length; i++) {
            TextView cell = new TextView(this);
            cell.setText(cells[i] == null ? "" : cells[i]);
            if (header) {
                cell.setTextAppearance(R.style.TextAppearance_Sme_Label);
                cell.setTextColor(getColor(R.color.on_primary_container));
            } else {
                cell.setTextAppearance(R.style.TextAppearance_Sme_Table);
            }
            int padH = dp(8);
            int padV = dp(10);
            cell.setPaddingRelative(padH, padV, padH, padV);
            boolean amount = !header && i >= endFrom;
            cell.setGravity(Gravity.CENTER_VERTICAL | (amount ? Gravity.END : Gravity.START));
            row.addView(cell);
        }
        return row;
    }

    private TableRow summaryRow(String label, String value, boolean money) {
        TableRow row = new TableRow(this);
        row.setBackgroundResource(R.drawable.bg_table_row);
        row.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        row.setDividerDrawable(getDrawable(R.drawable.divider_table));
        int padH = dp(8);
        int padV = dp(10);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextAppearance(R.style.TextAppearance_Sme_Label);
        labelView.setTextColor(getColor(R.color.on_primary_container));
        labelView.setBackgroundResource(R.drawable.bg_table_header);
        labelView.setPaddingRelative(padH, padV, padH, padV);
        labelView.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);

        TextView valueView = new TextView(this);
        valueView.setText(value == null ? "" : value);
        valueView.setTextAppearance(R.style.TextAppearance_Sme_Table);
        valueView.setPaddingRelative(padH, padV, padH, padV);
        valueView.setGravity(Gravity.CENTER_VERTICAL | (money ? Gravity.END : Gravity.START));

        row.addView(labelView);
        row.addView(valueView);
        return row;
    }

    private TableLayout tableFrame(TableLayout table) {
        table.setClipToOutline(true);
        table.setBackgroundResource(R.drawable.bg_table);
        int edge = dp(1);
        table.setPadding(edge, edge, edge, edge);
        table.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return table;
    }

    private void showApprovals(SubmissionDetail detail) {
        boolean has = detail.approvals != null && !detail.approvals.isEmpty();
        binding.approvalsHeader.setVisibility(has ? View.VISIBLE : View.GONE);
        binding.approvalsContainer.removeAllViews();
        if (!has) {
            return;
        }
        for (ApprovalDto approval : detail.approvals) {
            TextView decision = bodyText();
            String when = SubmissionText.prettyInstant(approval.actedAt);
            String who = approval.actorName == null ? "" : approval.actorName;
            String line = getString(R.string.decision_line, SubmissionText.status(this, approval.decision), who);
            if (when.length() > 0) {
                line = line + " · " + when;
            }
            decision.setText(line);
            binding.approvalsContainer.addView(decision);
            if (approval.comments != null && approval.comments.trim().length() > 0) {
                TextView comments = bodyText();
                comments.setPadding(0, 0, 0, dp(8));
                comments.setText(approval.comments);
                binding.approvalsContainer.addView(comments);
            }
        }
    }

    /** Submitter edits drafts. Approver decides on a budget that is waiting. Other roles only read. */
    private void showActions(SubmissionDetail detail) {
        boolean submitter = Roles.SUBMITTER.equals(session.getRole());
        boolean approver = Roles.APPROVER.equals(session.getRole());
        boolean editable = SubmissionStatus.editable(detail.status);
        boolean waiting = SubmissionStatus.SUBMITTED.equals(detail.status);
        boolean submitterActions = submitter && editable;
        boolean approverActions = approver && waiting;
        binding.editButton.setVisibility(submitterActions ? View.VISIBLE : View.GONE);
        binding.deleteButton.setVisibility(submitterActions ? View.VISIBLE : View.GONE);
        binding.submitButton.setVisibility(submitterActions ? View.VISIBLE : View.GONE);
        binding.approveButton.setVisibility(approverActions ? View.VISIBLE : View.GONE);
        binding.rejectButton.setVisibility(approverActions ? View.VISIBLE : View.GONE);
        binding.actionsHeader.setVisibility(submitterActions || approverActions ? View.VISIBLE : View.GONE);
        setActionsEnabled(!actionRunning);
    }

    /** Explains the next action for this role. It does not show another role's buttons. */
    private String nextStep(SubmissionDetail detail) {
        String status = detail.status;
        boolean submitter = Roles.SUBMITTER.equals(session.getRole());
        boolean approver = Roles.APPROVER.equals(session.getRole());
        if (SubmissionStatus.APPROVED.equals(status)) {
            return getString(R.string.next_approved);
        }
        if (SubmissionStatus.SUPERSEDED.equals(status)) {
            return getString(R.string.next_superseded);
        }
        if (submitter && SubmissionStatus.DRAFT.equals(status)) {
            return getString(R.string.next_submitter_draft);
        }
        if (submitter && SubmissionStatus.REJECTED.equals(status)) {
            return getString(R.string.next_submitter_rejected);
        }
        if (submitter && SubmissionStatus.SUBMITTED.equals(status)) {
            return getString(R.string.next_submitter_waiting);
        }
        if (approver && SubmissionStatus.SUBMITTED.equals(status)) {
            return getString(R.string.next_approver_waiting);
        }
        if (approver) {
            return getString(R.string.next_approver_idle);
        }
        if (submitter) {
            return getString(R.string.next_submitter_idle);
        }
        if (SubmissionStatus.SUBMITTED.equals(status)) {
            return getString(R.string.next_reader_waiting);
        }
        return getString(R.string.next_reader);
    }

    private void setActionsEnabled(boolean enabled) {
        binding.editButton.setEnabled(enabled);
        binding.deleteButton.setEnabled(enabled);
        binding.submitButton.setEnabled(enabled);
        binding.approveButton.setEnabled(enabled);
        binding.rejectButton.setEnabled(enabled);
        binding.actionProgress.setVisibility(enabled ? View.GONE : View.VISIBLE);
    }

    private void openEditor() {
        Intent intent = new Intent(this, SubmissionFormActivity.class);
        intent.putExtra(SubmissionFormActivity.EXTRA_ID, submissionId);
        startActivity(intent);
    }

    private void confirmSubmit() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.submit_confirm_title)
                .setMessage(R.string.submit_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.submit_for_approval, (dialog, which) -> runDetailAction(
                        api.submit(submissionId), R.string.submission_submitted))
                .show();
    }

    private void confirmApprove() {
        EditText input = commentField();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.approve_title)
                .setMessage(R.string.approve_message)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.approve, (dialog, which) -> runDetailAction(
                        api.approve(submissionId, new DecisionBody(textOf(input))),
                        R.string.submission_approved))
                .show();
    }

    private void confirmReject() {
        EditText input = commentField();
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reject_title)
                .setMessage(R.string.reject_message)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.reject, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String comments = textOf(input).trim();
            if (comments.length() == 0) {
                input.setError(getString(R.string.error_reject_comment));
                return;
            }
            dialog.dismiss();
            runDetailAction(api.reject(submissionId, new DecisionBody(comments)), R.string.submission_rejected);
        }));
        dialog.show();
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_confirm_title)
                .setMessage(R.string.delete_confirm_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_submission, (dialog, which) -> deleteSubmission())
                .show();
    }

    private void deleteSubmission() {
        actionRunning = true;
        setActionsEnabled(false);
        binding.errorText.setVisibility(View.GONE);
        Call<MessageDto> call = api.delete(submissionId);
        actionCall = call;
        call.enqueue(new Callback<MessageDto>() {
            @Override
            public void onResponse(@NonNull Call<MessageDto> responseCall, @NonNull Response<MessageDto> response) {
                if (responseCall.isCanceled() || isFinishing() || isDestroyed()) {
                    return;
                }
                if (SessionNav.expired(SubmissionDetailActivity.this, response)) {
                    return;
                }
                actionRunning = false;
                if (response.isSuccessful()) {
                    Toast.makeText(SubmissionDetailActivity.this, R.string.submission_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    setActionsEnabled(true);
                    showActionError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<MessageDto> responseCall, @NonNull Throwable t) {
                if (responseCall.isCanceled() || isFinishing() || isDestroyed()) {
                    return;
                }
                actionRunning = false;
                setActionsEnabled(true);
                showActionError(getString(R.string.error_no_connection));
            }
        });
    }

    private void runDetailAction(Call<SubmissionDetail> call, int successMessage) {
        actionRunning = true;
        setActionsEnabled(false);
        binding.errorText.setVisibility(View.GONE);
        actionCall = call;
        call.enqueue(new Callback<SubmissionDetail>() {
            @Override
            public void onResponse(@NonNull Call<SubmissionDetail> responseCall,
                                   @NonNull Response<SubmissionDetail> response) {
                if (responseCall.isCanceled() || isFinishing() || isDestroyed()) {
                    return;
                }
                if (SessionNav.expired(SubmissionDetailActivity.this, response)) {
                    return;
                }
                actionRunning = false;
                setActionsEnabled(true);
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(SubmissionDetailActivity.this, successMessage, Toast.LENGTH_SHORT).show();
                    show(response.body());
                } else {
                    showActionError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<SubmissionDetail> responseCall, @NonNull Throwable t) {
                if (responseCall.isCanceled() || isFinishing() || isDestroyed()) {
                    return;
                }
                actionRunning = false;
                setActionsEnabled(true);
                showActionError(getString(R.string.error_no_connection));
            }
        });
    }

    private EditText commentField() {
        EditText input = new EditText(this);
        input.setHint(R.string.comments);
        input.setMinLines(2);
        int pad = dp(16);
        input.setPadding(pad, pad, pad, pad);
        input.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return input;
    }

    private TextView bodyText() {
        TextView view = new TextView(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        view.setTextAppearance(R.style.TextAppearance_Sme_Body);
        view.setPadding(0, dp(2), 0, dp(2));
        return view;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void showLoadError(String message) {
        binding.content.setVisibility(View.GONE);
        binding.loadErrorText.setText(message);
        binding.loadErrorText.setVisibility(View.VISIBLE);
    }

    private void showActionError(String message) {
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    private static String textOf(EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    @Override
    protected void onDestroy() {
        if (loadCall != null) {
            loadCall.cancel();
        }
        if (actionCall != null) {
            actionCall.cancel();
        }
        super.onDestroy();
    }
}
