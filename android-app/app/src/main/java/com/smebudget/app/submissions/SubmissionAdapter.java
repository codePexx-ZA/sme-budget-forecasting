package com.smebudget.app.submissions;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smebudget.app.R;
import com.smebudget.app.databinding.ItemSubmissionBinding;

import java.util.ArrayList;
import java.util.List;

/** Rows on the submission list. Tap a row to open that budget. */
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
            binding.periodText.setText(itemView.getContext().getString(
                    R.string.period_range,
                    SubmissionText.prettyDate(item.periodStart),
                    SubmissionText.prettyDate(item.periodEnd)));
            binding.statusText.setText(SubmissionText.status(itemView.getContext(), item.status));
            binding.metaText.setText(itemView.getContext().getString(
                    R.string.line_meta,
                    item.lineItemCount,
                    SubmissionText.importMethod(itemView.getContext(), item.importMethod)));
            itemView.setOnClickListener(v -> listener.onClick(item));
        }
    }
}
