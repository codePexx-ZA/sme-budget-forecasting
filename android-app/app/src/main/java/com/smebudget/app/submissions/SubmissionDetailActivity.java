package com.smebudget.app.submissions;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
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

/**
 * One budget submission (Aiden): its lines, the profit subtotals for each period, and the
 * actions the logged-in role is allowed to take.
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
        binding.periodText.setText(getString(R.string.period_range,
                SubmissionText.prettyDate(detail.periodStart), SubmissionText.prettyDate(detail.periodEnd)));
        binding.submitterText.setText(getString(R.string.submitted_by,
                detail.submitterName == null ? "" : detail.submitterName));
        if (detail.submittedAt == null || detail.submittedAt.trim().length() == 0) {
            binding.submittedAtText.setVisibility(View.GONE);
        } else {
            binding.submittedAtText.setVisibility(View.VISIBLE);
            binding.submittedAtText.setText(getString(R.string.submitted_on, SubmissionText.prettyInstant(detail.submittedAt)));
        }
        binding.importText.setText(getString(R.string.import_value,
                SubmissionText.importMethod(this, detail.importMethod),
                SubmissionText.scale(this, detail.sourceScale)));
        showOptional(binding.fileText, detail.sourceFileName, R.string.file_value);
        String replaces = detail.replacesLabel != null ? detail.replacesLabel : detail.replacesSubmissionId;
        showOptional(binding.replacesText, replaces, R.string.replaces_value);
        showLines(detail);
        showApprovals(detail);
        showActions(detail);
    }

    private void showOptional(TextView view, String value, int template) {
        if (value == null || value.trim().length() == 0) {
            view.setVisibility(View.GONE);
        } else {
            view.setVisibility(View.VISIBLE);
            view.setText(getString(template, value));
        }
    }

    /** Lines are already ordered by period. Subtotals are printed after each period's lines. */
    private void showLines(SubmissionDetail detail) {
        binding.linesContainer.removeAllViews();
        if (detail.lineItems == null || detail.lineItems.isEmpty()) {
            TextView empty = bodyText();
            empty.setText(R.string.no_line_items);
            binding.linesContainer.addView(empty);
            return;
        }
        String currentPeriod = null;
        for (LineItemDto line : detail.lineItems) {
            if (currentPeriod == null || !currentPeriod.equals(line.periodStart)) {
                if (currentPeriod != null) {
                    addTotals(currentPeriod);
                }
                currentPeriod = line.periodStart;
                TextView heading = bodyText();
                heading.setTextAppearance(R.style.TextAppearance_Sme_Section);
                heading.setPadding(0, dp(16), 0, dp(4));
                heading.setText(SubmissionText.prettyDate(currentPeriod));
                binding.linesContainer.addView(heading);
            }
            TextView row = bodyText();
            String name = line.categoryName == null ? line.category : line.categoryName;
            row.setText(getString(R.string.line_amount, name,
                    SubmissionText.money(line.amount), SubmissionText.frequency(this, line.frequency)));
            binding.linesContainer.addView(row);
        }
        if (currentPeriod != null) {
            addTotals(currentPeriod);
        }
    }

    private void addTotals(String period) {
        PeriodTotalsDto totals = null;
        if (current != null && current.totals != null) {
            for (PeriodTotalsDto candidate : current.totals) {
                if (period.equals(candidate.periodStart)) {
                    totals = candidate;
                    break;
                }
            }
        }
        if (totals == null) {
            return;
        }
        TextView note = bodyText();
        note.setTextAppearance(R.style.TextAppearance_Sme_Caption);
        note.setPadding(0, dp(8), 0, 0);
        note.setText(R.string.subtotals_note);
        binding.linesContainer.addView(note);
        addTotalLine(R.string.gross_profit, totals.grossProfit);
        addTotalLine(R.string.operating_profit, totals.operatingProfit);
        addTotalLine(R.string.net_profit, totals.netProfit);
    }

    private void addTotalLine(int label, java.math.BigDecimal amount) {
        TextView row = bodyText();
        row.setText(getString(label) + ": " + SubmissionText.money(amount));
        binding.linesContainer.addView(row);
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
