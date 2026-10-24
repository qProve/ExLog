package com.qprovep.exlog.ui.session;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qprovep.exlog.R;
import com.qprovep.exlog.ui.session.SessionViewModel.SessionExerciseEntry;

import java.util.ArrayList;
import java.util.List;

public class ReorderExerciseAdapter extends RecyclerView.Adapter<ReorderExerciseAdapter.ViewHolder> {

    public interface OnDragStartListener {
        void onDragStarted(RecyclerView.ViewHolder viewHolder);
    }

    private final List<SessionExerciseEntry> entries = new ArrayList<>();
    private OnDragStartListener dragStartListener;

    public void setDragStartListener(OnDragStartListener dragStartListener) {
        this.dragStartListener = dragStartListener;
    }

    public void setEntries(List<SessionExerciseEntry> entries) {
        this.entries.clear();
        this.entries.addAll(entries);
        notifyDataSetChanged();
    }

    public List<SessionExerciseEntry> getEntries() {
        return entries;
    }

    public void onItemMove(int fromPosition, int toPosition) {
        SessionExerciseEntry moved = entries.remove(fromPosition);
        entries.add(toPosition, moved);
        notifyItemMoved(fromPosition, toPosition);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reorder_exercise, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(entries.get(position));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final ImageView dragHandle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.exercise_name);
            dragHandle = itemView.findViewById(R.id.drag_handle);
        }

        void bind(SessionExerciseEntry entry) {
            nameText.setText(entry.exercise.getName());

            dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && dragStartListener != null) {
                    dragStartListener.onDragStarted(this);
                }
                return false;
            });
        }
    }
}
