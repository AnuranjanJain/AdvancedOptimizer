package com.powersentinel.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.powersentinel.app.model.AppPowerReport;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AppReportAdapter extends RecyclerView.Adapter<AppReportAdapter.ViewHolder> {

    private final List<AppPowerReport> reports = new ArrayList<>();
    private final OnAppInteractionListener listener;
    private final Context context;

    public interface OnAppInteractionListener {
        /** Opens the native Android App Info screen (non-root disable/force-stop) */
        void onManageClicked(String packageName);
        /** Device Owner hide (optional, only if enrolled) */
        void onHideClicked(String packageName);
    }

    public AppReportAdapter(Context context, OnAppInteractionListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setReports(List<AppPowerReport> newReports) {
        AppReportDiffCallback diffCallback = new AppReportDiffCallback(this.reports, newReports);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(diffCallback);
        this.reports.clear();
        this.reports.addAll(newReports);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app_report, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppPowerReport report = reports.get(position);
        holder.bind(report, listener, context);
    }

    @Override
    public int getItemCount() {
        return reports.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView appName;
        private final TextView score;
        private final TextView details;
        private final MaterialButton btnHide;
        private final MaterialButton btnManage;

        ViewHolder(View view) {
            super(view);
            appName = view.findViewById(R.id.text_app_name);
            score = view.findViewById(R.id.text_score);
            details = view.findViewById(R.id.text_details);
            btnHide = view.findViewById(R.id.button_hide);
            btnManage = view.findViewById(R.id.button_disable);  // XML id kept for compatibility

            // Security: Prevent tapjacking
            btnHide.setFilterTouchesWhenObscured(true);
            btnManage.setFilterTouchesWhenObscured(true);
        }

        void bind(AppPowerReport report, OnAppInteractionListener listener, Context context) {
            appName.setText(report.app.label);
            score.setText(String.valueOf(report.score));
            score.setTextColor(colorForScore(report.score, context));
            appName.setTextColor(colorForScore(report.score, context));

            String lastUsed = report.lastUsedMillis == 0L
                    ? "Not seen in usage stats"
                    : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(report.lastUsedMillis);

            String detailsText = report.app.packageName
                    + "\nIntensity: " + report.intensity.label
                    + "\nServices: " + report.app.declaredServices.size()
                    + "\nCache: " + bytes(report.cacheBytes)
                    + "\nForeground today: " + minutes(report.foregroundMillis)
                    + "\nLast used: " + lastUsed
                    + "\n\n" + report.recommendation;
            details.setText(detailsText);

            btnManage.setOnClickListener(v -> listener.onManageClicked(report.app.packageName));
            btnHide.setOnClickListener(v -> listener.onHideClicked(report.app.packageName));
        }

        private int colorForScore(int score, Context context) {
            int colorResId;
            if (score >= 75) {
                colorResId = R.color.scoreCritical;
            } else if (score >= 52) {
                colorResId = R.color.scoreAggressive;
            } else if (score >= 28) {
                colorResId = R.color.scoreBalanced;
            } else {
                colorResId = R.color.scoreLow;
            }
            return context.getColor(colorResId);
        }

        private String bytes(long bytes) {
            if (bytes <= 0L) {
                return "0 MB";
            }
            return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
        }

        private String minutes(long millis) {
            return String.format(Locale.US, "%.0f min", millis / 60000.0);
        }
    }

    private static class AppReportDiffCallback extends DiffUtil.Callback {
        private final List<AppPowerReport> oldList;
        private final List<AppPowerReport> newList;

        AppReportDiffCallback(List<AppPowerReport> oldList, List<AppPowerReport> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).app.packageName
                    .equals(newList.get(newItemPosition).app.packageName);
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            AppPowerReport oldReport = oldList.get(oldItemPosition);
            AppPowerReport newReport = newList.get(newItemPosition);
            return oldReport.score == newReport.score
                    && oldReport.foregroundMillis == newReport.foregroundMillis
                    && oldReport.cacheBytes == newReport.cacheBytes;
        }
    }
}
