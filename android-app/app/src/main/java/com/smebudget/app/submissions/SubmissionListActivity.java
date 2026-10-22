package com.smebudget.app.submissions;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.smebudget.app.R;
import com.smebudget.app.auth.Roles;
import com.smebudget.app.auth.SessionManager;
import com.smebudget.app.databinding.ActivitySubmissionListBinding;
import com.smebudget.app.network.ApiClient;
import com.smebudget.app.network.ApiErrors;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Lists this business unit's budgets and filters them by status. */
public class SubmissionListActivity extends AppCompatActivity {

    private ActivitySubmissionListBinding binding;
    private SessionManager session;
    private SubmissionAdapter adapter;
    private Call<List<SubmissionSummary>> listCall;
    private final List<SubmissionSummary> allItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            finish();
            return;
        }

        binding = ActivitySubmissionListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.submissionList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubmissionAdapter(this::openDetail);
        binding.submissionList.setAdapter(adapter);

        boolean submitter = Roles.SUBMITTER.equals(session.getRole());
        binding.newButton.setVisibility(submitter ? View.VISIBLE : View.GONE);
        binding.newButton.setOnClickListener(v ->
                startActivity(new Intent(this, SubmissionFormActivity.class)));
        binding.roleGuide.setText(listGuideRes());
        // An approver opens on Waiting, so the inbox is the first thing on the screen.
        if (Roles.APPROVER.equals(session.getRole())) {
            binding.chipWaiting.setChecked(true);
        }
        binding.statusFilters.setOnCheckedStateChangeListener((group, checkedIds) -> showRows());
    }

    /** Where to tap next. The New submission button stays hidden unless this user is a submitter. */
    private int listGuideRes() {
        String role = session.getRole();
        if (Roles.SUBMITTER.equals(role)) {
            return R.string.list_guide_submitter;
        }
        if (Roles.APPROVER.equals(role)) {
            return R.string.list_guide_approver;
        }
        return R.string.list_guide_reader;
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void openDetail(SubmissionSummary item) {
        Intent intent = new Intent(this, SubmissionDetailActivity.class);
        intent.putExtra(SubmissionDetailActivity.EXTRA_ID, item.submissionId);
        startActivity(intent);
    }

    /** Reloads the list. Called again when the user comes back from a submission. */
    private void load() {
        if (listCall != null) {
            listCall.cancel();
        }
        binding.progress.setVisibility(View.VISIBLE);
        binding.errorText.setVisibility(View.GONE);
        binding.emptyText.setVisibility(View.GONE);
        listCall = ApiClient.create(this, SubmissionsApi.class).list();
        listCall.enqueue(new Callback<List<SubmissionSummary>>() {
            @Override
            public void onResponse(@NonNull Call<List<SubmissionSummary>> call,
                                   @NonNull Response<List<SubmissionSummary>> response) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) {
                    return;
                }
                binding.progress.setVisibility(View.GONE);
                if (SessionNav.expired(SubmissionListActivity.this, response)) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    allItems.clear();
                    allItems.addAll(response.body());
                    showRows();
                } else {
                    showError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<SubmissionSummary>> call, @NonNull Throwable t) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) {
                    return;
                }
                binding.progress.setVisibility(View.GONE);
                showError(getString(R.string.error_no_connection));
            }
        });
    }

    /** Applies the status chip. With no chip selected, every status is shown, including superseded. */
    private void showRows() {
        String status = selectedStatus();
        List<SubmissionSummary> visible = new ArrayList<>();
        for (SubmissionSummary item : allItems) {
            if (status == null || status.equals(item.status)) {
                visible.add(item);
            }
        }
        if (Roles.APPROVER.equals(session.getRole())) {
            visible.sort(SubmissionListActivity::waitingFirst);
        }
        adapter.setItems(visible);
        if (!visible.isEmpty()) {
            binding.emptyText.setVisibility(View.GONE);
            return;
        }
        if (allItems.isEmpty()) {
            int message = Roles.SUBMITTER.equals(session.getRole())
                    ? R.string.empty_submissions_submitter
                    : R.string.empty_submissions_other;
            binding.emptyText.setText(message);
        } else {
            binding.emptyText.setText(R.string.empty_filter);
        }
        binding.emptyText.setVisibility(View.VISIBLE);
    }

    private String selectedStatus() {
        int id = binding.statusFilters.getCheckedChipId();
        if (id == R.id.chipWaiting) {
            return SubmissionStatus.SUBMITTED;
        }
        if (id == R.id.chipDraft) {
            return SubmissionStatus.DRAFT;
        }
        if (id == R.id.chipRejected) {
            return SubmissionStatus.REJECTED;
        }
        if (id == R.id.chipApproved) {
            return SubmissionStatus.APPROVED;
        }
        return null;
    }

    /** Waiting rows first. TimSort is stable, so the period order from the API stays inside each group. */
    private static int waitingFirst(SubmissionSummary left, SubmissionSummary right) {
        int leftRank = SubmissionStatus.SUBMITTED.equals(left.status) ? 0 : 1;
        int rightRank = SubmissionStatus.SUBMITTED.equals(right.status) ? 0 : 1;
        return leftRank - rightRank;
    }

    private void showError(String message) {
        binding.errorText.setText(message);
        binding.errorText.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (listCall != null) {
            listCall.cancel();
        }
        super.onDestroy();
    }
}
