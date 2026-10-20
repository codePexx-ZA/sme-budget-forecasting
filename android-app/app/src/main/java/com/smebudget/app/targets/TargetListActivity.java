package com.smebudget.app.targets;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.smebudget.app.R;
import com.smebudget.app.auth.Roles;
import com.smebudget.app.auth.SessionManager;
import com.smebudget.app.databinding.ActivityTargetListBinding;
import com.smebudget.app.network.ApiClient;
import com.smebudget.app.network.ApiErrors;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * KPI target list (Boitu). A decision maker can add or open a target. The list reloads when
 * the user comes back from the form.
 */
public class TargetListActivity extends AppCompatActivity {

    private ActivityTargetListBinding binding;
    private SessionManager session;
    private TargetAdapter adapter;
    private Call<List<TargetDto>> listCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            finish();
            return;
        }

        binding = ActivityTargetListBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new TargetAdapter(this::openForm);
        binding.targetList.setLayoutManager(new LinearLayoutManager(this));
        binding.targetList.setAdapter(adapter);

        boolean decisionMaker = Roles.DECISION_MAKER.equals(session.getRole());
        binding.newButton.setVisibility(decisionMaker ? View.VISIBLE : View.GONE);
        binding.newButton.setOnClickListener(v -> startActivity(new Intent(this, TargetFormActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void openForm(TargetDto item) {
        if (!Roles.DECISION_MAKER.equals(session.getRole())) {
            return;
        }
        Intent intent = new Intent(this, TargetFormActivity.class);
        intent.putExtra(TargetFormActivity.EXTRA_ID, item.targetId);
        startActivity(intent);
    }

    private void load() {
        if (listCall != null) {
            listCall.cancel();
        }
        binding.progress.setVisibility(View.VISIBLE);
        binding.errorText.setVisibility(View.GONE);
        binding.emptyText.setVisibility(View.GONE);
        listCall = ApiClient.create(this, TargetsApi.class).list();
        listCall.enqueue(new Callback<List<TargetDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<TargetDto>> call, @NonNull Response<List<TargetDto>> response) {
                if (call.isCanceled() || isFinishing() || isDestroyed()) {
                    return;
                }
                binding.progress.setVisibility(View.GONE);
                if (SessionNav.expired(TargetListActivity.this, response)) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    adapter.setItems(response.body());
                    binding.emptyText.setVisibility(response.body().isEmpty() ? View.VISIBLE : View.GONE);
                } else {
                    showError(ApiErrors.message(response, getString(R.string.error_unexpected)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<TargetDto>> call, @NonNull Throwable t) {
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
