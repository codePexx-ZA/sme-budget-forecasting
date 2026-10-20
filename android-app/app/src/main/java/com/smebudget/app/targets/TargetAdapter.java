package com.smebudget.app.targets;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.smebudget.app.databinding.ItemTargetBinding;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/** Rows on the KPI target list. */
public class TargetAdapter extends RecyclerView.Adapter<TargetAdapter.Holder> {

    interface Listener {
        void onClick(TargetDto item);
    }

    private final List<TargetDto> items = new ArrayList<>();
    private final Listener listener;
    private final DecimalFormat amount = new DecimalFormat("#,##0.00");

    TargetAdapter(Listener listener) {
        this.listener = listener;
    }

    void setItems(List<TargetDto> next) {
        items.clear();
        if (next != null) {
            items.addAll(next);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTargetBinding binding = ItemTargetBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TargetDto item = items.get(position);
        holder.binding.metricText.setText(item.metricName);
        holder.binding.valueText.setText(formatValue(item));
        holder.binding.periodText.setText(item.targetPeriod);
        holder.binding.getRoot().setOnClickListener(v -> listener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatValue(TargetDto item) {
        String number = "";
        if (item.targetValue != null) {
            number = amount.format(item.targetValue);
        }
        String unit = item.measurementUnit == null ? "" : item.measurementUnit;
        if ("ZAR".equalsIgnoreCase(unit)) {
            return "R " + number;
        }
        return number + " " + unit;
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemTargetBinding binding;

        Holder(ItemTargetBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
