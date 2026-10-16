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

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lists the business unit's budget submissions (Aiden).
 * A submitter can start a new one. Everyone logged in can open a row to read it.
 */
public class SubmissionListActivity extends AppCompatActivity {

    private ActivitySubmissionListBinding binding;
    private SessionManager session;
    private SubmissionAdapter adapter;
    private Call<List<SubmissionSummary>> listCall;

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
                    adapter.setItems(response.body());
                    if (response.body().isEmpty()) {
                        int message = Roles.SUBMITTER.equals(session.getRole())
                                ? R.string.empty_submissions_submitter
                                : R.string.empty_submissions_other;
                        binding.emptyText.setText(message);
                        binding.emptyText.setVisibility(View.VISIBLE);
                    }
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
