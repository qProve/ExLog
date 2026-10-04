package com.qprovep.exlog.ui.history;

import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qprovep.exlog.R;
import com.qprovep.exlog.data.dao.SessionDao;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private List<SessionDao.SessionHistoryItem> sessions = new ArrayList<>();
    private final OnItemClickListener listener;
    private final OnItemLongClickListener longClickListener;

    public interface OnItemClickListener {
        void onItemClick(SessionDao.SessionHistoryItem item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(SessionDao.SessionHistoryItem item);
    }

    public HistoryAdapter(OnItemClickListener listener, OnItemLongClickListener longClickListener) {
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    public void setSessions(List<SessionDao.SessionHistoryItem> sessions) {
        this.sessions = sessions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_history_session, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(sessions.get(position));
    }

    @Override
    public int getItemCount() {
        return sessions == null ? 0 : sessions.size();
    }

    private String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = (seconds / (60 * 60)) % 24;

        if (h > 0) {
            return String.format("%d:%02d:%02d", h, m, s);
        } else {
            return String.format("%02d:%02d", m, s);
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView dateText;
        private final TextView nameText;
        private final TextView durationText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.history_date);
            nameText = itemView.findViewById(R.id.history_workout_name);
            durationText = itemView.findViewById(R.id.history_duration);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onItemClick(sessions.get(position));
                }
            });

            itemView.setOnLongClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && longClickListener != null) {
                    longClickListener.onItemLongClick(sessions.get(position));
                    return true;
                }
                return false;
            });
        }

        void bind(SessionDao.SessionHistoryItem item) {
            nameText.setText(item.workoutName);
            dateText.setText(DateFormat.format("dd.MM yyyy", new Date(item.date)));
            durationText.setText("Duration: " + formatDuration(item.durationMs));
        }
    }
}
