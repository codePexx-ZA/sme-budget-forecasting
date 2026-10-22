package com.smebudget.app.submissions;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smebudget.app.R;
import com.smebudget.app.databinding.ItemSubmissionBinding;

import java.util.ArrayList;
import java.util.List;

/** Binds each budget to a row on the submissions list. */
public class SubmissionAdapter extends RecyclerView.Adapter<SubmissionAdapter.Holder> {

    /** Called when a row is tapped. */
    public interface Listener {
        void onClick(SubmissionSummary item);
    }

    private final List<SubmissionSummary> items = new ArrayList<>();
    private final Listener listener;

    public SubmissionAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<SubmissionSummary> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSubmissionBinding binding = ItemSubmissionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    final class Holder extends RecyclerView.ViewHolder {

        private final ItemSubmissionBinding binding;

        Holder(ItemSubmissionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(SubmissionSummary item) {
            Context context = itemView.getContext();
            binding.periodText.setText(context.getString(
                    R.string.period_range,
                    SubmissionText.prettyDate(item.periodStart),
                    SubmissionText.prettyDate(item.periodEnd)));
            binding.statusText.setText(SubmissionText.status(context, item.status));
            StatusChips.apply(binding.statusText, item.status);
            String meaning = SubmissionText.statusMeaning(context, item.status);
            binding.statusMeaning.setText(meaning);
            binding.statusMeaning.setVisibility(meaning.length() == 0 ? View.GONE : View.VISIBLE);
            String comment = item.latestRejectionComment == null ? "" : item.latestRejectionComment.trim();
            if (SubmissionStatus.REJECTED.equals(item.status) && comment.length() > 0) {
                binding.rejectionText.setText(context.getString(R.string.rejection_comment, comment));
                binding.rejectionText.setVisibility(View.VISIBLE);
            } else {
                binding.rejectionText.setVisibility(View.GONE);
            }
            binding.metaText.setText(context.getString(
                    R.string.line_meta,
                    item.lineItemCount,
                    SubmissionText.importMethod(context, item.importMethod)));
            itemView.setOnClickListener(v -> listener.onClick(item));
        }
    }
}
