package com.qprovep.exlog.ui.workout;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.qprovep.exlog.R;
import com.qprovep.exlog.data.entity.ExerciseTemplate;

import java.util.ArrayList;
import java.util.List;

public class WorkoutExerciseEditAdapter extends RecyclerView.Adapter<WorkoutExerciseEditAdapter.ViewHolder> {

    public static class RotationItem {
        public ExerciseTemplate exercise;
        public int sessionCount;
        public double referenceWeight;
        public int targetSets;
        public int targetReps;

        public RotationItem(ExerciseTemplate exercise, int sessionCount,
                            double referenceWeight, int targetSets, int targetReps) {
            this.exercise = exercise;
            this.sessionCount = sessionCount;
            this.referenceWeight = referenceWeight;
            this.targetSets = targetSets;
            this.targetReps = targetReps;
        }
    }

    public static class ExerciseEntry {
        public ExerciseTemplate exercise;
        public double referenceWeight;
        public int targetSets;
        public int targetReps;
        public final List<WorkoutExerciseEditAdapter.RotationItem> rotation = new ArrayList<>();

        public ExerciseEntry(ExerciseTemplate exercise, double referenceWeight, int targetSets, int targetReps) {
            this.exercise = exercise;
            this.referenceWeight = referenceWeight;
            this.targetSets = targetSets;
            this.targetReps = targetReps;
        }
    }

    public static RotationItem resolveActive(ExerciseEntry entry, int sessionsDone) {
        if (entry.rotation.isEmpty()) {
            return new RotationItem(entry.exercise, 1, entry.referenceWeight, entry.targetSets, entry.targetReps);
        }

        int total = 0;
        for (RotationItem item : entry.rotation) {
            total += Math.max(1, item.sessionCount);
        }
        int pos = sessionsDone % total;

        int acc = 0;
        for (RotationItem item : entry.rotation) {
            acc += Math.max(1, item.sessionCount);
            if (pos < acc) return item;
        }
        return entry.rotation.get(0);
    }

    private final List<ExerciseEntry> entries = new ArrayList<>();
    private OnDragStartListener dragStartListener;

    public interface  OnRotationClickListener {
        void onRotationClicked(int position, ExerciseEntry entry);
    }
    private OnRotationClickListener rotationClickListener;
    public void setRotationClickListener(OnRotationClickListener listener) {
        this.rotationClickListener = listener;
    }

    public interface OnDragStartListener {
        void onDragStarted(RecyclerView.ViewHolder viewHolder);
    }

    public void setDragStartListener(OnDragStartListener listener) {
        this.dragStartListener = listener;
    }

    public void setEntries(List<ExerciseEntry> newEntries) {
        entries.clear();
        entries.addAll(newEntries);
        notifyDataSetChanged();
    }

    public void addEntry(ExerciseEntry entry) {
        entries.add(entry);
        notifyItemInserted(entries.size() - 1);
    }

    public List<ExerciseEntry> getEntries() {
        return entries;
    }

    public void moveUp(int position) {
        if (position > 0 && position < entries.size()) {
            java.util.Collections.swap(entries, position, position - 1);
            notifyItemMoved(position, position - 1);
            notifyItemChanged(position - 1);
            notifyItemChanged(position);
        }
    }

    public void moveDown(int position) {
        if (position >= 0 && position < entries.size() - 1) {
            java.util.Collections.swap(entries, position, position + 1);
            notifyItemMoved(position, position + 1);
            notifyItemChanged(position);
            notifyItemChanged(position + 1);
        }
    }

    public boolean onItemMove(int fromPosition, int toPosition) {
        java.util.Collections.swap(entries, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        return true;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_workout_exercise, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(entries.get(position));
        holder.dragHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && dragStartListener != null) {
                dragStartListener.onDragStarted(holder);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    private static String formatRotation(ExerciseEntry entry) {
        StringBuilder sb = new StringBuilder("↻ ");
        for (int i = 0; i < entry.rotation.size(); i++) {
            RotationItem item = entry.rotation.get(i);
            if (i > 0) sb.append(" → ");
            sb.append(item.sessionCount).append("× ").append(item.exercise.getName());
        }
        return sb.toString();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final EditText refWeightEdit;
        private final EditText targetSetsEdit;
        private final EditText targetRepsEdit;
        private final ImageButton removeBtn;
        public final ImageView dragHandle;
        private final ImageButton rotationBtn;
        private final TextView rotationSummary;
        private ExerciseEntry currentEntry;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.exercise_name);
            refWeightEdit = itemView.findViewById(R.id.edit_ref_weight);
            targetSetsEdit = itemView.findViewById(R.id.edit_target_sets);
            targetRepsEdit = itemView.findViewById(R.id.edit_target_reps);
            removeBtn = itemView.findViewById(R.id.btn_remove);
            dragHandle = itemView.findViewById(R.id.drag_handle);
            rotationBtn = itemView.findViewById(R.id.btn_rotation);
            rotationSummary = itemView.findViewById(R.id.rotation_summary);

            removeBtn.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    entries.remove(pos);
                    notifyItemRemoved(pos);
                }
            });

            rotationBtn.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && rotationClickListener != null) {
                    rotationClickListener.onRotationClicked(pos, entries.get(pos));
                }
            });

            refWeightEdit.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (currentEntry == null) return;
                    double val;
                    try {
                        val = Double.parseDouble(s.toString());
                    } catch (NumberFormatException e) {
                        val = 0;
                    }
                    if (currentEntry.rotation.isEmpty()) {
                        currentEntry.referenceWeight = val;
                    } else {
                        currentEntry.rotation.get(0).referenceWeight = val;
                    }
                }
            });

            targetSetsEdit.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (currentEntry == null) return;
                    int val;
                    try {
                        val = Integer.parseInt(s.toString());
                    } catch (NumberFormatException e) {
                        val = 0;
                    }
                    if (currentEntry.rotation.isEmpty()) {
                        currentEntry.targetSets = val;
                    } else {
                        currentEntry.rotation.get(0).targetSets = val;
                    }
                }
            });

            targetRepsEdit.addTextChangedListener(new SimpleTextWatcher() {
                @Override
                public void afterTextChanged(Editable s) {
                    if (currentEntry == null) return;
                    int val;
                    try {
                        val = Integer.parseInt(s.toString());
                    } catch (NumberFormatException e) {
                        val = 0;
                    }
                    if (currentEntry.rotation.isEmpty()) {
                        currentEntry.targetReps = val;
                    } else {
                        currentEntry.rotation.get(0).targetReps = val;
                    }
                }
            });
        }

        void bind(ExerciseEntry entry) {
            currentEntry = entry;
            nameText.setText(entry.exercise.getName());

            double refWeight = entry.rotation.isEmpty()
                    ? entry.referenceWeight : entry.rotation.get(0).referenceWeight;
            int tSets = entry.rotation.isEmpty()
                    ? entry.targetSets : entry.rotation.get(0).targetSets;
            int tReps = entry.rotation.isEmpty()
                    ? entry.targetReps : entry.rotation.get(0).targetReps;

            refWeightEdit.setText(refWeight > 0 ? String.valueOf(refWeight) : "");
            targetSetsEdit.setText(tSets > 0 ? String.valueOf(tSets) : "");
            targetRepsEdit.setText(tReps > 0 ? String.valueOf(tReps) : "");

            if (entry.rotation.isEmpty()) {
                rotationSummary.setVisibility((View.GONE));
            } else {
                rotationSummary.setVisibility(View.VISIBLE);
                rotationSummary.setText(formatRotation(entry));
            }
        }
    }

    private static abstract class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
