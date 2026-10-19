package com.qprovep.exlog.ui.workout;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.qprovep.exlog.R;
import com.qprovep.exlog.data.entity.ExerciseTemplate;
import com.qprovep.exlog.data.entity.RotationItem;
import com.qprovep.exlog.data.entity.WorkoutExercise;
import com.qprovep.exlog.data.entity.WorkoutTemplate;
import com.qprovep.exlog.data.relation.RotationWithTemplate;
import com.qprovep.exlog.data.relation.WorkoutExerciseWithTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class WorkoutDetailFragment extends Fragment {

    private WorkoutViewModel viewModel;
    private WorkoutExerciseEditAdapter exerciseAdapter;
    private EditText nameEdit;
    private int workoutId = -1;
    private WorkoutTemplate existingWorkout;
    private List<ExerciseTemplate> allExercisesCached;
    private List<String> categoriesCached = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_workout_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(WorkoutViewModel.class);

        nameEdit = view.findViewById(R.id.edit_workout_name);
        RecyclerView recyclerView = view.findViewById(R.id.workout_exercises_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        exerciseAdapter = new WorkoutExerciseEditAdapter();
        recyclerView.setAdapter(exerciseAdapter);

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return exerciseAdapter.onItemMove(viewHolder.getAdapterPosition(),
                        target.getAdapterPosition());
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        itemTouchHelper.attachToRecyclerView(recyclerView);
        exerciseAdapter.setDragStartListener(itemTouchHelper::startDrag);
        exerciseAdapter.setRotationClickListener((pos, entry) -> showRotationExercisePicker(pos, entry));
        MaterialButton btnAddExercise = view.findViewById(R.id.btn_add_exercise);
        MaterialButton btnSave = view.findViewById(R.id.btn_save_workout);

        viewModel.getAllExercises().observe(getViewLifecycleOwner(), exercises -> {
            allExercisesCached = exercises;
            buildCategoryList();
        });

        if (getArguments() != null) {
            workoutId = getArguments().getInt("workoutId", -1);
        }

        if (workoutId != -1) {
            viewModel.getWorkoutWithExercises(workoutId).observe(getViewLifecycleOwner(), data -> {
                if (data == null)
                    return;

                existingWorkout = data.workoutTemplate;
                nameEdit.setText(existingWorkout.getName());

                if (exerciseAdapter.getEntries().isEmpty() && data.exercises != null) {
                    List<WorkoutExerciseEditAdapter.ExerciseEntry> entries = new ArrayList<>();
                    List<WorkoutExerciseWithTemplate> sortedExercises = new ArrayList<>(data.exercises);
                    Collections.sort(sortedExercises, Comparator.comparingInt(
                            e -> e.workoutExercise.getOrderIndex()));
                    for (WorkoutExerciseWithTemplate weWithTemplate : sortedExercises) {
                        WorkoutExerciseEditAdapter.ExerciseEntry entry =
                                new WorkoutExerciseEditAdapter.ExerciseEntry(
                                        weWithTemplate.exerciseTemplate,
                                        weWithTemplate.workoutExercise.getReferenceWeight(),
                                        weWithTemplate.workoutExercise.getTargetSets(),
                                        weWithTemplate.workoutExercise.getTargetReps());

                        if (weWithTemplate.rotation != null && weWithTemplate.rotation.size() > 1) {
                            List<RotationWithTemplate> rotSorted = new ArrayList<>(weWithTemplate.rotation);
                            Collections.sort(rotSorted, Comparator.comparingInt(
                                    r -> r.rotationItem.getOrderIndex()));
                            for (RotationWithTemplate r : rotSorted) {
                                entry.rotation.add(new WorkoutExerciseEditAdapter.RotationItem(
                                        r.exerciseTemplate,
                                        r.rotationItem.getSessionCount(),
                                        r.rotationItem.getReferenceWeight(),
                                        r.rotationItem.getTargetSets(),
                                        r.rotationItem.getTargetReps()));
                            }
                        }

                        entries.add(entry);
                    }
                    exerciseAdapter.setEntries(entries);
                }
            });
        }

        btnAddExercise.setOnClickListener(v -> showExercisePicker());
        btnSave.setOnClickListener(v -> saveWorkout());
    }

    private void buildCategoryList() {
        if (allExercisesCached == null)
            return;
        Set<String> cats = new LinkedHashSet<>();
        for (ExerciseTemplate ex : allExercisesCached) {
            if (ex.getCategory() != null && !ex.getCategory().isEmpty()) {
                cats.add(ex.getCategory());
            }
        }
        categoriesCached.clear();
        categoriesCached.addAll(cats);
    }

    private void showExercisePicker() {
        if (allExercisesCached == null || allExercisesCached.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.pick_exercises)
                    .setMessage(R.string.no_exercises)
                    .setPositiveButton(R.string.cancel, null)
                    .show();
            return;
        }

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_exercise_picker, null);

        EditText searchEdit = dialogView.findViewById(R.id.search_exercises);
        AutoCompleteTextView categoryFilter = dialogView.findViewById(R.id.filter_category);
        RecyclerView pickerRecycler = dialogView.findViewById(R.id.exercise_picker_recycler);
        pickerRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        ExercisePickerAdapter pickerAdapter = new ExercisePickerAdapter();

        Set<Integer> preSelected = new HashSet<>();
        for (WorkoutExerciseEditAdapter.ExerciseEntry entry : exerciseAdapter.getEntries()) {
            preSelected.add(entry.exercise.getId());
        }

        pickerAdapter.setExercises(allExercisesCached, preSelected);
        pickerRecycler.setAdapter(pickerAdapter);

        List<String> filterOptions = new ArrayList<>();
        filterOptions.add(getString(R.string.all_categories));
        filterOptions.add("\u2014");
        filterOptions.addAll(categoriesCached);

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, filterOptions);
        categoryFilter.setAdapter(catAdapter);
        categoryFilter.setText(getString(R.string.all_categories), false);

        categoryFilter.setOnItemClickListener((parent, view, position, id) -> {
            String selected = filterOptions.get(position);
            if (selected.equals(getString(R.string.all_categories))) {
                pickerAdapter.filterByCategory("");
            } else {
                pickerAdapter.filterByCategory(selected);
            }
        });

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                pickerAdapter.filterByName(s.toString());
            }
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.pick_exercises)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    Set<Integer> selectedIds = pickerAdapter.getSelectedIds();
                    applySelectedExercises(selectedIds);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applySelectedExercises(Set<Integer> selectedIds) {
        List<WorkoutExerciseEditAdapter.ExerciseEntry> existingEntries = new ArrayList<>();
        for (WorkoutExerciseEditAdapter.ExerciseEntry entry : exerciseAdapter.getEntries()) {
            if (selectedIds.contains(entry.exercise.getId())) {
                existingEntries.add(entry);
            }
        }

        Set<Integer> existingIds = new HashSet<>();
        for (WorkoutExerciseEditAdapter.ExerciseEntry entry : existingEntries) {
            existingIds.add(entry.exercise.getId());
        }

        for (ExerciseTemplate ex : allExercisesCached) {
            if (selectedIds.contains(ex.getId()) && !existingIds.contains(ex.getId())) {
                existingEntries.add(new WorkoutExerciseEditAdapter.ExerciseEntry(ex, 0, 3, 10));
            }
        }

        exerciseAdapter.setEntries(existingEntries);
    }

    private void saveWorkout() {
        String name = nameEdit.getText().toString().trim();
        if (name.isEmpty()) {
            nameEdit.setError("Name required");
            return;
        }

        List<WorkoutExerciseEditAdapter.ExerciseEntry> entries = exerciseAdapter.getEntries();
        List<WorkoutExercise> workoutExercises = new ArrayList<>();
        List<List<RotationItem>> rotations = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            WorkoutExerciseEditAdapter.ExerciseEntry e = entries.get(i);

            int exerciseId = e.exercise.getId();
            double refWeight = e.referenceWeight;
            int tSets = e.targetSets;
            int tReps = e.targetReps;

            if (!e.rotation.isEmpty()) {
                WorkoutExerciseEditAdapter.RotationItem first = e.rotation.get(0);
                exerciseId = first.exercise.getId();
                refWeight = first.referenceWeight;
                tSets = first.targetSets;
                tReps = first.targetReps;
            }

            workoutExercises.add(new WorkoutExercise(0, exerciseId, i, refWeight, tSets, tReps));

            List<RotationItem> rotEnts = new ArrayList<>();
            for (int r = 0; r < e.rotation.size(); r++) {
                WorkoutExerciseEditAdapter.RotationItem item = e.rotation.get(r);
                rotEnts.add(new RotationItem(
                        0,
                        item.exercise.getId(),
                        r,
                        Math.max(1, item.sessionCount),
                        item.referenceWeight,
                        item.targetSets,
                        item.targetReps
                ));
            }
            rotations.add(rotEnts);
        }

        if (existingWorkout != null) {
            existingWorkout.setName(name);
            viewModel.updateWorkout(existingWorkout, workoutExercises, rotations);
        } else {
            WorkoutTemplate newWorkout = new WorkoutTemplate(name);
            viewModel.insertWorkout(newWorkout, workoutExercises, null, rotations);
        }

        Navigation.findNavController(requireView()).popBackStack();
    }

    private void showRotationExercisePicker(int position, WorkoutExerciseEditAdapter.ExerciseEntry entry) {
        if (allExercisesCached == null || allExercisesCached.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.rotating_excercise)
                    .setMessage(R.string.no_exercises)
                    .setPositiveButton(R.string.cancel, null)
                    .show();
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_exercise_picker, null);

        EditText searchEdit = dialogView.findViewById(R.id.search_exercises);
        AutoCompleteTextView categoryFilter = dialogView.findViewById(R.id.filter_category);
        RecyclerView pickerRecycler = dialogView.findViewById(R.id.exercise_picker_recycler);
        pickerRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        ExercisePickerAdapter pickerAdapter = new ExercisePickerAdapter();

        Set<Integer> preSelected = new HashSet<>();
        for (WorkoutExerciseEditAdapter.RotationItem item : entry.rotation) {
            preSelected.add(item.exercise.getId());
        }
        if (entry.rotation.isEmpty()) {
            preSelected.add(entry.exercise.getId());
        }

        pickerAdapter.setExercises(allExercisesCached, preSelected);
        pickerRecycler.setAdapter(pickerAdapter);

        List<String> filterOptions = new ArrayList<>();
        filterOptions.add(getString(R.string.all_categories));
        filterOptions.add("\u2014");
        filterOptions.addAll(categoriesCached);

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_dropdown_item_1line, filterOptions);
        categoryFilter.setAdapter(catAdapter);
        categoryFilter.setText(getString(R.string.all_categories), false);

        categoryFilter.setOnItemClickListener((parent, view, pos, id) -> {
            String selected = filterOptions.get(pos);
            if (selected.equals(getString(R.string.all_categories))) {
                pickerAdapter.filterByCategory("");
            } else {
                pickerAdapter.filterByCategory(selected);
            }
        });

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override
            public void afterTextChanged(Editable s) {
                pickerAdapter.filterByName(s.toString());
            }
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.rotating_excercise)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) ->
                        applyRotationSelection(position, entry, pickerAdapter.getSelectedIds()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applyRotationSelection(int position, WorkoutExerciseEditAdapter.ExerciseEntry entry,
                                        Set<Integer> selectedIds) {
        if (selectedIds == null || selectedIds.isEmpty()) {
            entry.rotation.clear();
            exerciseAdapter.notifyItemChanged(position);
            return;
        }

        List<WorkoutExerciseEditAdapter.RotationItem> working = new ArrayList<>();
        for (WorkoutExerciseEditAdapter.RotationItem item : entry.rotation) {
            if (selectedIds.contains(item.exercise.getId())) {
                working.add(item);
            }
        }
        Set<Integer> present = new HashSet<>();
        for (WorkoutExerciseEditAdapter.RotationItem item : working) {
            present.add(item.exercise.getId());
        }
        for (ExerciseTemplate ex : allExercisesCached) {
            if (selectedIds.contains(ex.getId()) && !present.contains(ex.getId())) {
                working.add(new WorkoutExerciseEditAdapter.RotationItem(ex, 1, 0, 3, 10));
            }
        }
        showRotationConfigDialog(position, entry, working);
    }

    private void showRotationConfigDialog(int position, WorkoutExerciseEditAdapter.ExerciseEntry entry,
                                          List<WorkoutExerciseEditAdapter.RotationItem> working) {
        if (working.size() <= 1) {
            entry.rotation.clear();
            exerciseAdapter.notifyItemChanged(position);
            return;
        }

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_rotation_config, null);
        LinearLayout rowsContainer = dialogView.findViewById(R.id.rotation_rows_container);

        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> buildRotationRows(rowsContainer, working, rebuild[0]);
        rebuild[0].run();

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.rotating_excercise)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (d, w) -> {
                    entry.rotation.clear();
                    entry.rotation.addAll(working);
                    exerciseAdapter.notifyItemChanged(position);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void buildRotationRows(LinearLayout container,
                                   List<WorkoutExerciseEditAdapter.RotationItem> items, Runnable rebuild) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (int i = 0; i < items.size(); i++) {
            final int idx = i;
            WorkoutExerciseEditAdapter.RotationItem item = items.get(i);
            View row = inflater.inflate(R.layout.item_rotation_config_row, container, false);

            TextView name = row.findViewById(R.id.rotation_name);
            EditText countEdit = row.findViewById(R.id.edit_session_count);
            EditText weightEdit = row.findViewById(R.id.edit_rotation_weight);
            EditText setsEdit = row.findViewById(R.id.edit_rotation_sets);
            EditText repsEdit = row.findViewById(R.id.edit_rotation_reps);
            ImageButton upBtn = row.findViewById(R.id.btn_rotation_up);
            ImageButton downBtn = row.findViewById(R.id.btn_rotation_down);
            ImageButton removeBtn = row.findViewById(R.id.btn_rotation_remove);

            name.setText((idx + 1) + ". " + item.exercise.getName());
            countEdit.setText(item.sessionCount > 0 ? String.valueOf(item.sessionCount) : "");
            weightEdit.setText(item.referenceWeight > 0 ? String.valueOf(item.referenceWeight) : "");
            setsEdit.setText(item.targetSets > 0 ? String.valueOf(item.targetSets) : "");
            repsEdit.setText(item.targetReps > 0 ? String.valueOf(item.targetReps) : "");

            countEdit.addTextChangedListener(watcher(s -> item.sessionCount = parseIntOr(s, 1)));
            weightEdit.addTextChangedListener(watcher(s -> item.referenceWeight = parseDoubleOr(s, 0)));
            setsEdit.addTextChangedListener(watcher(s -> item.targetSets = parseIntOr(s, 0)));
            repsEdit.addTextChangedListener(watcher(s -> item.targetReps = parseIntOr(s, 0)));

            upBtn.setOnClickListener(v -> {
                if (idx > 0) { Collections.swap(items, idx, idx - 1); rebuild.run(); }
            });
            downBtn.setOnClickListener(v -> {
                if (idx < items.size() - 1) { Collections.swap(items, idx, idx + 1); rebuild.run(); }
            });
            removeBtn.setOnClickListener(v -> {
                items.remove(idx);
                if (items.size() <= 1) {
                    items.clear();
                }
                rebuild.run();
            });

            container.addView(row);
        }
    }

    private interface AfterChange { void afterChange(String s); }

    private TextWatcher watcher(AfterChange action) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { action.afterChange(s.toString()); }
        };
    }

    private int parseIntOr(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return def; }
    }

    private double parseDoubleOr(String s, double def) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); } catch (NumberFormatException e) { return def; }
    }
}