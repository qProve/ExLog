package com.qprovep.exlog.ui.session;

import android.app.Application;
import android.os.Looper;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.qprovep.exlog.R;
import com.qprovep.exlog.data.AppDatabase;
import com.qprovep.exlog.data.dao.SetLogDao;
import com.qprovep.exlog.data.entity.ExerciseTemplate;
import com.qprovep.exlog.data.entity.SetLog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.util.Log;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.ViewHolder> {

    private List<SessionViewModel.SessionExerciseEntry> exercises = new ArrayList<>();
    private final SessionViewModel viewModel;
    private final Set<Integer> expandedPositions = new HashSet<>();
    private final Set<Integer> infoOpenPositions = new HashSet<>();
    private boolean firstBindDone = false;
    private final SetLogDao setLogDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<Integer, List<SetLog>> lastLogCache = new HashMap<>();

    public interface OnDragStartListener {
        void onDragStarted(RecyclerView.ViewHolder viewHolder);
    }
    private OnDragStartListener dragStartListener;

    public void setDragStartListener(OnDragStartListener dragStartListener) {
        this.dragStartListener = dragStartListener;
    }

    public void onItemMove(int fromPosition, int toPosition) {
        viewModel.swapExercises(fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    private static final float WEIGHT_STEP = 0.5f;
    private static final float WEIGHT_MAX = 200f;
    private static final int WEIGHT_PICKER_MAX = (int) (WEIGHT_MAX / WEIGHT_STEP);
    private static final String[] WEIGHT_VALUES;
    private static final int REPS_MAX = 100;

    static {
        WEIGHT_VALUES = new String[WEIGHT_PICKER_MAX + 1];
        for (int i = 0; i <= WEIGHT_PICKER_MAX; i++) {
            float val = i * WEIGHT_STEP;
            if (val == (int) val) {
                WEIGHT_VALUES[i] = String.valueOf((int) val);
            } else {
                WEIGHT_VALUES[i] = String.valueOf(val);
            }
        }
    }

    public SessionAdapter(SessionViewModel viewModel) {
        this.viewModel = viewModel;
        AppDatabase db = AppDatabase.getInstance(viewModel.getApplication());
        setLogDao = db.setLogDao();
    }

    public void setExercises(List<SessionViewModel.SessionExerciseEntry> exercises) {
        this.exercises = exercises;
        if (!firstBindDone && !exercises.isEmpty()) {
            expandedPositions.add(0);
            firstBindDone = true;
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session_exercise, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(exercises.get(position), position);
    }

    @Override
    public int getItemCount() {
        return exercises.size();
    }

    private int weightToPickerIndex(float weight) {
        int index = Math.round(weight / WEIGHT_STEP);
        return Math.max(0, Math.min(index, WEIGHT_PICKER_MAX));
    }

    private float pickerIndexToWeight(int index) {
        return index * WEIGHT_STEP;
    }

    private boolean areAllSetsDone(SessionViewModel.SessionExerciseEntry entry) {
        if (entry.sets.isEmpty())
            return false;
        for (SessionViewModel.SetEntry set : entry.sets) {
            if (!set.isCompleted)
                return false;
        }
        return true;
    }

    private void autoAdvance(int currentExerciseIndex) {
        expandedPositions.remove(currentExerciseIndex);

        for (int next = currentExerciseIndex + 1; next < exercises.size(); next++) {
            if (!areAllSetsDone(exercises.get(next))) {
                expandedPositions.add(next);
                notifyItemChanged(currentExerciseIndex);
                notifyItemChanged(next);
                return;
            }
        }
        notifyItemChanged(currentExerciseIndex);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final ImageView expandIcon;
        private final TextView doneIndicator;
        private final ImageView dragHandle;
        private final MaterialButton btnInfo;
        private final MaterialButton btnAddSet;
        private final MaterialButton btnRemoveSet;
        private final LinearLayout header;
        private final LinearLayout content;
        private final LinearLayout setsContainer;
        private final LinearLayout infoSection;
        private final TextView infoCategory;
        private final TextView infoNote;
        private final TextView infoLink;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.exercise_name);
            expandIcon = itemView.findViewById(R.id.expand_icon);
            doneIndicator = itemView.findViewById(R.id.done_indicator);
            dragHandle = itemView.findViewById(R.id.drag_handle);
            btnInfo = itemView.findViewById(R.id.btn_info);
            btnAddSet = itemView.findViewById(R.id.btn_add_set);
            btnRemoveSet = itemView.findViewById(R.id.btn_remove_set);
            header = itemView.findViewById(R.id.exercise_header);
            content = itemView.findViewById(R.id.exercise_content);
            setsContainer = itemView.findViewById(R.id.sets_container);
            infoSection = itemView.findViewById(R.id.info_section);
            infoCategory = itemView.findViewById(R.id.info_category);
            infoNote = itemView.findViewById(R.id.info_note);
            infoLink = itemView.findViewById(R.id.info_link);
        }

        void bind(SessionViewModel.SessionExerciseEntry entry, final int exerciseIndex) {
            final int exerciseId = entry.exercise.getId();
            nameText.setText(entry.exercise.getName());

            dragHandle.setOnTouchListener((v, event) -> {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN && dragStartListener != null) {
                    dragStartListener.onDragStarted(this);
                }
                return false;
            });

            boolean isExpanded = expandedPositions.contains(exerciseIndex);
            content.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            expandIcon.setRotation(isExpanded ? 180f : 0f);

            doneIndicator.setVisibility(areAllSetsDone(entry) ? View.VISIBLE : View.GONE);

            boolean hasInfo = !TextUtils.isEmpty(entry.exercise.getCategory())
                    || !TextUtils.isEmpty(entry.exercise.getNote())
                    || !TextUtils.isEmpty(entry.exercise.getExampleLink());

            btnInfo.setVisibility(hasInfo ? View.VISIBLE : View.GONE);

            boolean infoOpen = infoOpenPositions.contains(exerciseIndex);
            bindInfoSection(entry.exercise, infoOpen);
            btnInfo.setText(infoOpen ? "▴ Hide Info" : "▾ Show Info");

            btnInfo.setOnClickListener(v -> {
                if (infoOpenPositions.contains(exerciseIndex)) {
                    infoOpenPositions.remove(exerciseIndex);
                    infoSection.setVisibility(View.GONE);
                    btnInfo.setText(itemView.getContext().getString(R.string.show_info));
                } else {
                    infoOpenPositions.add(exerciseIndex);
                    bindInfoSection(entry.exercise, true);
                    infoSection.setVisibility(View.VISIBLE);
                    btnInfo.setText(itemView.getContext().getString(R.string.hide_info));
                }
            });

            header.setOnClickListener(v -> {
                if (expandedPositions.contains(exerciseIndex)) {
                    expandedPositions.remove(exerciseIndex);
                    content.setVisibility(View.GONE);
                    expandIcon.setRotation(0f);
                } else {
                    expandedPositions.add(exerciseIndex);
                    content.setVisibility(View.VISIBLE);
                    expandIcon.setRotation(180f);
                }
            });

            setsContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());

            final List<SetLog>[] cached = new List[1];
            synchronized (lastLogCache) {
                cached[0] = lastLogCache.get(exerciseId);
            }

            for (int i = 0; i < entry.sets.size(); i++) {
                final int setIndex = i;
                SessionViewModel.SetEntry set = entry.sets.get(i);

                View setRow = inflater.inflate(R.layout.item_set_input, setsContainer, false);
                TextView setNum = setRow.findViewById(R.id.set_number);
                NumberPicker weightPicker = setRow.findViewById(R.id.picker_weight);
                NumberPicker repsPicker = setRow.findViewById(R.id.picker_reps);
                MaterialCheckBox checkbox = setRow.findViewById(R.id.checkbox_completed);
                TextView prevWeight = setRow.findViewById(R.id.prev_weight);
                TextView prevReps = setRow.findViewById(R.id.prev_reps);

                if (cached[0] != null) {
                    applyLastLog(cached[0], setIndex, prevWeight, prevReps);
                } else {
                    executor.execute(() -> {
                        List<SetLog> lastLogs = setLogDao.getLastSetLogsForExercise(entry.exercise.getId());
                        synchronized (lastLogCache) {
                            lastLogCache.put(exerciseId, lastLogs);
                        }
                        mainHandler.post(() -> applyLastLog(lastLogs, setIndex, prevWeight, prevReps));
                    });
                }

                setNum.setText(String.valueOf(set.setNumber));

                weightPicker.setMinValue(0);
                weightPicker.setMaxValue(WEIGHT_PICKER_MAX);
                weightPicker.setDisplayedValues(WEIGHT_VALUES);
                weightPicker.setWrapSelectorWheel(false);
                weightPicker.setValue(weightToPickerIndex(set.weight));

                repsPicker.setMinValue(0);
                repsPicker.setMaxValue(REPS_MAX);
                repsPicker.setWrapSelectorWheel(false);
                repsPicker.setValue(set.reps);

                checkbox.setChecked(set.isCompleted);

                weightPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                    float w = pickerIndexToWeight(newVal);
                    viewModel.updateSet(exerciseIndex, setIndex, w, repsPicker.getValue(), checkbox.isChecked());
                });

                repsPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                    float w = pickerIndexToWeight(weightPicker.getValue());
                    viewModel.updateSet(exerciseIndex, setIndex, w, newVal, checkbox.isChecked());
                });

                checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    float w = pickerIndexToWeight(weightPicker.getValue());
                    viewModel.updateSet(exerciseIndex, setIndex, w, repsPicker.getValue(), isChecked);

                    if (areAllSetsDone(entry)) {
                        doneIndicator.setVisibility(View.VISIBLE);
                        autoAdvance(exerciseIndex);
                    } else {
                        doneIndicator.setVisibility(View.GONE);
                    }
                });

                prevWeight.setOnClickListener(v -> {
                    setSetLikeLastSession(exerciseIndex, setIndex, prevWeight, weightPicker, prevReps, repsPicker, checkbox);
                });

                prevReps.setOnClickListener(v -> {
                    setSetLikeLastSession(exerciseIndex, setIndex, prevWeight, weightPicker, prevReps, repsPicker, checkbox);
                });

                setsContainer.addView(setRow);
            }

            btnAddSet.setOnClickListener(v -> viewModel.addSet(exerciseIndex));
            btnRemoveSet.setOnClickListener(v -> viewModel.removeSet(exerciseIndex));
        }

        private void applyLastLog(List<SetLog> lastLogs, int setIndex, TextView prevWeight, TextView prevReps) {
            if (lastLogs == null || lastLogs.isEmpty() || setIndex >= lastLogs.size()) {
                prevWeight.setText("");
                prevReps.setText("");
                return;
            };

            SetLog last = lastLogs.get(setIndex);
            prevWeight.setText(String.valueOf(last.getWeight()));
            prevReps.setText(String.valueOf(last.getReps()));
        }

        private void bindInfoSection(ExerciseTemplate exercise, boolean visible) {
            infoSection.setVisibility(visible ? View.VISIBLE : View.GONE);

            if (!TextUtils.isEmpty(exercise.getCategory())) {
                infoCategory.setText("Category: " + exercise.getCategory());
                infoCategory.setVisibility(View.VISIBLE);
            } else {
                infoCategory.setVisibility(View.GONE);
            }

            if (!TextUtils.isEmpty(exercise.getNote())) {
                infoNote.setText("Note: " + exercise.getNote());
                infoNote.setVisibility(View.VISIBLE);
            } else {
                infoNote.setVisibility(View.GONE);
            }

            if (!TextUtils.isEmpty(exercise.getExampleLink())) {
                infoLink.setText(exercise.getExampleLink());
                infoLink.setVisibility(View.VISIBLE);
            } else {
                infoLink.setVisibility(View.GONE);
            }
        }
    }

    private void setSetLikeLastSession(
            int excerciseInd,
            int setInd,
            TextView prevWeight,
            NumberPicker weightPicker,
            TextView prevReps,
            NumberPicker repsPicker,
            MaterialCheckBox chbox
    ) {
        float w = parseWeight(prevWeight.getText().toString());
        int r = parseInt(prevReps.getText().toString());

        if (w < 0 && r < 0) return;

        if (w >= 0) weightPicker.setValue(weightToPickerIndex(w));
        if (r >= 0) repsPicker.setValue(r);

        viewModel.updateSet(
            excerciseInd,
            setInd,
            pickerIndexToWeight(weightPicker.getValue()),
            repsPicker.getValue(),
            chbox.isChecked()
        );
    }

    private float parseWeight(String weight) {
        try {
            return Float.parseFloat(weight.replace(',', '.'));
        } catch (NumberFormatException e) {
            return -1f;
        }
    }

    private int parseInt(String reps) {
        try {
            return Integer.parseInt(reps.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
