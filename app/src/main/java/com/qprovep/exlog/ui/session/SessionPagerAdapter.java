package com.qprovep.exlog.ui.session;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
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

public class SessionPagerAdapter extends RecyclerView.Adapter<SessionPagerAdapter.ViewHolder> {

    public interface OnAutoAdvanceListener {
        void onAutoAdvance(int fromIndex, int toIndex);
    }

    private List<SessionViewModel.SessionExerciseEntry> exercises = new ArrayList<>();
    private final SessionViewModel viewModel;
    private final Set<Integer> infoOpenExerciseIds = new HashSet<>();
    private final Map<Integer, Integer> currentSetIndexByExerciseId = new HashMap<>();
    private final SetLogDao setLogDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<Integer, List<SetLog>> lastLogCache = new HashMap<>();

    private OnAutoAdvanceListener autoAdvanceListener;
    private Runnable onCompletionChanged;

    public void setAutoAdvanceListener(OnAutoAdvanceListener listener) {
        this.autoAdvanceListener = listener;
    }

    public void setOnCompletionChanged(Runnable listener) {
        this.onCompletionChanged = listener;
    }

    public boolean isExerciseDone(int position) {
        if (position < 0 || position >= exercises.size()) return false;
        return areAllSetsDone(exercises.get(position));
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

    public SessionPagerAdapter(SessionViewModel viewModel) {
        this.viewModel = viewModel;
        AppDatabase db = AppDatabase.getInstance(viewModel.getApplication());
        setLogDao = db.setLogDao();
    }

    public void setExercises(List<SessionViewModel.SessionExerciseEntry> exercises) {
        this.exercises = exercises;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session_page, parent, false);
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

    private int findNextIncompleteExercise(int fromIndex) {
        for (int next = fromIndex + 1; next < exercises.size(); next++) {
            if (!areAllSetsDone(exercises.get(next))) {
                return next;
            }
        }
        return -1;
    }

    private int getOrInitCurrentSetIndex(SessionViewModel.SessionExerciseEntry entry) {
        int exerciseId = entry.exercise.getId();
        int count = entry.sets.size();
        if (count == 0)
            return 0;

        Integer stored = currentSetIndexByExerciseId.get(exerciseId);
        if (stored != null && stored >= 0 && stored < count) {
            return stored;
        }

        int firstIncomplete = -1;
        for (int i = 0; i < count; i++) {
            if (!entry.sets.get(i).isCompleted) {
                firstIncomplete = i;
                break;
            }
        }

        int index = firstIncomplete == -1 ? 0 : firstIncomplete;
        currentSetIndexByExerciseId.put(exerciseId, index);
        return index;
    }

    private int findNextIncompleteSet(SessionViewModel.SessionExerciseEntry entry, int fromIndex) {
        int count = entry.sets.size();
        for (int step = 1; step <= count; step++) {
            int index = (fromIndex + step) % count;
            if (!entry.sets.get(index).isCompleted) {
                return index;
            }
        }
        return -1;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText;
        private final TextView doneIndicator;
        private final MaterialButton btnInfo;
        private final MaterialButton btnAddSet;
        private final MaterialButton btnRemoveSet;
        private final TextView setLabel;
        private final LinearLayout setsDots;
        private final ImageButton btnPrevSet;
        private final ImageButton btnNextSet;
        private final FrameLayout setRowContainer;
        private final LinearLayout infoSection;
        private final TextView infoCategory;
        private final TextView infoNote;
        private final TextView infoLink;

        int boundExerciseId = -1;
        final int[] currentSet = { 0 };
        TextView prevWeight;
        TextView prevWeightLabel;
        TextView prevReps;
        TextView prevRepsLabel;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.exercise_name);
            doneIndicator = itemView.findViewById(R.id.done_indicator);
            btnInfo = itemView.findViewById(R.id.btn_info);
            btnAddSet = itemView.findViewById(R.id.btn_add_set);
            btnRemoveSet = itemView.findViewById(R.id.btn_remove_set);
            setLabel = itemView.findViewById(R.id.set_label);
            setsDots = itemView.findViewById(R.id.sets_dots);
            btnPrevSet = itemView.findViewById(R.id.btn_prev_set);
            btnNextSet = itemView.findViewById(R.id.btn_next_set);
            setRowContainer = itemView.findViewById(R.id.set_row_container);
            infoSection = itemView.findViewById(R.id.info_section);
            infoCategory = itemView.findViewById(R.id.info_category);
            infoNote = itemView.findViewById(R.id.info_note);
            infoLink = itemView.findViewById(R.id.info_link);
        }

        void bind(SessionViewModel.SessionExerciseEntry entry, final int exerciseIndex) {
            boundExerciseId = entry.exercise.getId();
            nameText.setText(entry.exercise.getName());

            doneIndicator.setVisibility(areAllSetsDone(entry) ? View.VISIBLE : View.GONE);

            boolean hasInfo = !TextUtils.isEmpty(entry.exercise.getCategory())
                    || !TextUtils.isEmpty(entry.exercise.getNote())
                    || !TextUtils.isEmpty(entry.exercise.getExampleLink());

            btnInfo.setVisibility(hasInfo ? View.VISIBLE : View.GONE);

            boolean infoOpen = infoOpenExerciseIds.contains(boundExerciseId);
            bindInfoSection(entry.exercise, infoOpen);
            btnInfo.setText(infoOpen ? R.string.hide_info : R.string.show_info);

            btnInfo.setOnClickListener(v -> {
                if (infoOpenExerciseIds.contains(boundExerciseId)) {
                    infoOpenExerciseIds.remove(boundExerciseId);
                    infoSection.setVisibility(View.GONE);
                    btnInfo.setText(R.string.show_info);
                } else {
                    infoOpenExerciseIds.add(boundExerciseId);
                    bindInfoSection(entry.exercise, true);
                    infoSection.setVisibility(View.VISIBLE);
                    btnInfo.setText(R.string.hide_info);
                }
            });

            btnPrevSet.setOnClickListener(v -> {
                int count = entry.sets.size();
                if (count < 2)
                    return;
                showSet(entry, exerciseIndex, (currentSet[0] - 1 + count) % count);
            });

            btnNextSet.setOnClickListener(v -> {
                int count = entry.sets.size();
                if (count < 2)
                    return;
                showSet(entry, exerciseIndex, (currentSet[0] + 1) % count);
            });

            btnAddSet.setOnClickListener(v -> {
                viewModel.addSet(exerciseIndex);
                int newLast = entry.sets.size() - 1;
                if (newLast >= 0) {
                    currentSetIndexByExerciseId.put(boundExerciseId, newLast);
                }
            });

            btnRemoveSet.setOnClickListener(v -> {
                viewModel.removeSet(exerciseIndex);
                int count = entry.sets.size();
                int clamped = Math.min(currentSet[0], count - 1);
                currentSetIndexByExerciseId.put(boundExerciseId, Math.max(0, clamped));
            });

            showSet(entry, exerciseIndex, getOrInitCurrentSetIndex(entry));
        }


        void showSet(SessionViewModel.SessionExerciseEntry entry, final int exerciseIndex, int setIndex) {
            currentSet[0] = setIndex;
            currentSetIndexByExerciseId.put(boundExerciseId, setIndex);

            int count = entry.sets.size();
            setLabel.setText(itemView.getContext().getString(R.string.set_position, setIndex + 1, count));

            boolean multi = count > 1;
            btnPrevSet.setVisibility(multi ? View.VISIBLE : View.GONE);
            btnNextSet.setVisibility(multi ? View.VISIBLE : View.GONE);

            buildSetDots(entry, exerciseIndex, setIndex);
            buildSetRow(entry, exerciseIndex, setIndex);
        }

        private void buildSetDots(SessionViewModel.SessionExerciseEntry entry, final int exerciseIndex, int selected) {
            setsDots.removeAllViews();
            int size = itemView.getResources().getDimensionPixelSize(R.dimen.set_dot_size);
            int margin = itemView.getResources().getDimensionPixelSize(R.dimen.set_dot_margin);
            float inactiveAlpha = itemView.getResources().getFloat(R.dimen.dot_inactive_alpha);

            for (int i = 0; i < entry.sets.size(); i++) {
                final int setIndex = i;
                View dot = new View(itemView.getContext());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
                params.setMargins(margin, margin, margin, margin);
                dot.setLayoutParams(params);
                dot.setBackgroundResource(R.drawable.dot_white);

                TypedValue ripple = new TypedValue();
                itemView.getContext().getTheme()
                        .resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
                dot.setForeground(itemView.getResources()
                        .getDrawable(ripple.resourceId, itemView.getContext().getTheme()));

                boolean isDone = entry.sets.get(i).isCompleted;
                if (isDone) {
                    dot.setBackgroundResource(R.drawable.dot_done);
                    dot.setAlpha(1f);
                } else {
                    dot.setBackgroundResource(R.drawable.dot_white);
                    dot.setAlpha(i == selected ? 1f : inactiveAlpha);
                }

                dot.setOnClickListener(v -> {
                    if (currentSet[0] != setIndex) {
                        showSet(entry, exerciseIndex, setIndex);
                    }
                });

                setsDots.addView(dot);
            }
        }

        private void buildSetRow(SessionViewModel.SessionExerciseEntry entry, final int exerciseIndex, int setIndex) {
            SessionViewModel.SetEntry set = entry.sets.get(setIndex);

            setRowContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
            View setRow = inflater.inflate(R.layout.item_set_input, setRowContainer, false);

            TextView setNum = setRow.findViewById(R.id.set_number);
            NumberPicker weightPicker = setRow.findViewById(R.id.picker_weight);
            NumberPicker repsPicker = setRow.findViewById(R.id.picker_reps);
            MaterialCheckBox checkbox = setRow.findViewById(R.id.checkbox_completed);
            prevWeight = setRow.findViewById(R.id.prev_weight);
            prevWeightLabel = setRow.findViewById(R.id.prev_weight_label);
            prevReps = setRow.findViewById(R.id.prev_reps);
            prevRepsLabel = setRow.findViewById(R.id.prev_reps_label);

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

            setRowContainer.addView(setRow);

            applyPrevValues(entry, setIndex);

            weightPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                float w = pickerIndexToWeight(newVal);
                viewModel.updateSet(exerciseIndex, currentSet[0], w, repsPicker.getValue(), checkbox.isChecked());
            });

            repsPicker.setOnValueChangedListener((picker, oldVal, newVal) -> {
                float w = pickerIndexToWeight(weightPicker.getValue());
                viewModel.updateSet(exerciseIndex, currentSet[0], w, newVal, checkbox.isChecked());
            });

            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                float w = pickerIndexToWeight(weightPicker.getValue());
                viewModel.updateSet(exerciseIndex, currentSet[0], w, repsPicker.getValue(), isChecked);

                if (onCompletionChanged != null) {
                    onCompletionChanged.run();
                }

                if (areAllSetsDone(entry)) {
                    doneIndicator.setVisibility(View.VISIBLE);
                    if (autoAdvanceListener != null) {
                        int next = findNextIncompleteExercise(exerciseIndex);
                        if (next != -1) {
                            autoAdvanceListener.onAutoAdvance(exerciseIndex, next);
                        }
                    }
                } else {
                    doneIndicator.setVisibility(View.GONE);
                    if (isChecked) {
                        int next = findNextIncompleteSet(entry, currentSet[0]);
                        if (next != -1) {
                            showSet(entry, exerciseIndex, next);
                        }
                    }
                }
            });

            prevWeight.setOnClickListener(v -> {
                setSetLikeLastSession(entry, exerciseIndex, weightPicker, repsPicker, checkbox);
            });

            prevReps.setOnClickListener(v -> {
                setSetLikeLastSession(entry, exerciseIndex, weightPicker, repsPicker, checkbox);
            });
        }

        private void applyPrevValues(SessionViewModel.SessionExerciseEntry entry, int setIndex) {
            final int exerciseId = entry.exercise.getId();

            List<SetLog> cached;
            synchronized (lastLogCache) {
                cached = lastLogCache.get(exerciseId);
            }

            if (cached != null) {
                applyLastLog(cached, setIndex);
                return;
            }

            executor.execute(() -> {
                List<SetLog> lastLogs = setLogDao.getLastSetLogsForExercise(exerciseId);
                synchronized (lastLogCache) {
                    lastLogCache.put(exerciseId, lastLogs);
                }
                mainHandler.post(() -> {
                    if (boundExerciseId == exerciseId) {
                        applyLastLog(lastLogs, currentSet[0]);
                    }
                });
            });
        }

        private void applyLastLog(List<SetLog> lastLogs, int setIndex) {
            if (lastLogs == null || lastLogs.isEmpty() || setIndex >= lastLogs.size()) {
                prevWeight.setVisibility(View.INVISIBLE);
                prevWeightLabel.setVisibility(View.INVISIBLE);
                prevReps.setVisibility(View.INVISIBLE);
                prevRepsLabel.setVisibility(View.INVISIBLE);
                return;
            }

            SetLog last = lastLogs.get(setIndex);
            prevWeight.setVisibility(View.VISIBLE);
            prevWeightLabel.setVisibility(View.VISIBLE);
            prevReps.setVisibility(View.VISIBLE);
            prevRepsLabel.setVisibility(View.VISIBLE);
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

        private void setSetLikeLastSession(
                SessionViewModel.SessionExerciseEntry entry,
                int exerciseIndex,
                NumberPicker weightPicker,
                NumberPicker repsPicker,
                MaterialCheckBox checkbox
        ) {
            float w = parseWeight(prevWeight.getText().toString());
            int r = parseInt(prevReps.getText().toString());

            if (w < 0 && r < 0) return;

            if (w >= 0) weightPicker.setValue(weightToPickerIndex(w));
            if (r >= 0) repsPicker.setValue(r);

            viewModel.updateSet(
                exerciseIndex,
                currentSet[0],
                pickerIndexToWeight(weightPicker.getValue()),
                repsPicker.getValue(),
                checkbox.isChecked()
            );
        }
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
