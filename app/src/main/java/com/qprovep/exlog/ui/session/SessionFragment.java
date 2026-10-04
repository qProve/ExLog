package com.qprovep.exlog.ui.session;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.qprovep.exlog.R;
import com.qprovep.exlog.data.entity.ExerciseTemplate;
import com.qprovep.exlog.data.entity.WorkoutTemplate;
import com.qprovep.exlog.ui.session.SessionViewModel.SessionExerciseEntry;
import com.qprovep.exlog.ui.workout.ExercisePickerAdapter;
import com.qprovep.exlog.ui.workout.WorkoutViewModel;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SessionFragment extends Fragment {

    private SessionViewModel viewModel;
    private SessionAdapter adapter;
    private TextView timerText;
    
    private List<ExerciseTemplate> allExercisesCached;
    private final List<String> categoriesCached = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_session, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(SessionViewModel.class);
        WorkoutViewModel workoutViewModel = new ViewModelProvider(this).get(WorkoutViewModel.class);
        workoutViewModel.getAllExercises().observe(getViewLifecycleOwner(), exercises -> {
            allExercisesCached = exercises;
            buildCategoryList();
        });

        MaterialToolbar toolbar = view.findViewById(R.id.session_toolbar);
        timerText = view.findViewById(R.id.session_timer);
        RecyclerView recyclerView = view.findViewById(R.id.session_exercises_recycler);
        MaterialButton finishBtn = view.findViewById(R.id.btn_finish_session);
        MaterialButton pauseResumeBtn = view.findViewById(R.id.btn_pause_resume);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SessionAdapter(viewModel);
        recyclerView.setAdapter(adapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.START | ItemTouchHelper.END
        ) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                adapter.onItemMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                viewModel.removeExercise(position);
            }
        });
        touchHelper.attachToRecyclerView(recyclerView);

        adapter.setDragStartListener(touchHelper::startDrag);

        toolbar.inflateMenu(R.menu.menu_session);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_add_exercise) {
                showExercisePicker();
                return true;
            }
            return false;
        });

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        WorkoutTemplate current = viewModel.getCurrentWorkout().getValue();
                        if (current != null) {
                            showDiscardDialog(view);
                        } else {
                            setEnabled(false);
                            requireActivity().getOnBackPressedDispatcher().onBackPressed();
                        }
                    }
                });

        viewModel.getCurrentWorkout().observe(getViewLifecycleOwner(), template -> {
            if (template != null) {
                toolbar.setTitle(template.getName());
            } else {
                toolbar.setTitle(R.string.app_name);
            }
        });

        viewModel.getSessionExercises().observe(getViewLifecycleOwner(), entries -> {
            if (entries != null) {
                adapter.setExercises(entries);
            }
        });

        viewModel.getElapsedTime().observe(getViewLifecycleOwner(), millis -> {
            long seconds = millis / 1000;
            long secs = seconds % 60;
            long mins = (seconds / 60) % 60;
            long hours = seconds / 3600;
            timerText.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, mins, secs));
        });

        viewModel.isTimerPaused().observe(getViewLifecycleOwner(), paused -> {
            if (paused != null && paused) {
                pauseResumeBtn.setText(getString(R.string.resume));
                pauseResumeBtn.setIconResource(android.R.drawable.ic_media_play);
            } else {
                pauseResumeBtn.setText(getString(R.string.pause));
                pauseResumeBtn.setIconResource(android.R.drawable.ic_media_pause);
            }
        });

        pauseResumeBtn.setOnClickListener(v -> viewModel.togglePause());

        finishBtn.setOnClickListener(v -> {
            WorkoutTemplate current = viewModel.getCurrentWorkout().getValue();
            if (current == null) {
                Toast.makeText(requireContext(), "No active session", Toast.LENGTH_SHORT).show();
                return;
            }

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Finish Workout?")
                    .setMessage("Are you sure you want to finish this session? Incomplete sets will be discarded.")
                    .setPositiveButton("Finish", (dialog, which) -> {
                        viewModel.finishSession("");
                        Toast.makeText(requireContext(), "Session saved!", Toast.LENGTH_SHORT).show();
                        Navigation.findNavController(view).popBackStack();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        if (getArguments() != null) {
            int startWorkoutId = getArguments().getInt("startWorkoutId", -1);
            if (startWorkoutId != -1) {
                viewModel.startSession(startWorkoutId);
                getArguments().remove("startWorkoutId");
            }
        }
    }

    private void showDiscardDialog(View view) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Leave Session?")
                .setMessage("You have an active session. Do you want to discard it?")
                .setPositiveButton("Discard", (dialog, which) -> {
                    viewModel.discardSession();
                    Navigation.findNavController(view).popBackStack();
                })
                .setNeutralButton("Save & Leave", (dialog, which) -> {
                    viewModel.finishSession("");
                    Toast.makeText(requireContext(), "Session saved!", Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(view).popBackStack();
                })
                .setNegativeButton("Stay", null)
                .show();
    }

    private void buildCategoryList() {
        if (allExercisesCached == null) return;
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
        List<SessionExerciseEntry> currentEntries = viewModel.getSessionExercises().getValue();
        if (currentEntries != null) {
            for (SessionExerciseEntry entry : currentEntries) {
                preSelected.add(entry.exercise.getId());
            }
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

        categoryFilter.setOnItemClickListener((parent, v, position, id) -> {
            String selected = filterOptions.get(position);
            if (selected.equals(getString(R.string.all_categories))) {
                pickerAdapter.filterByCategory("");
            } else {
                pickerAdapter.filterByCategory(selected);
            }
        });

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
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
                    applySelectedExercises(selectedIds, preSelected);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void applySelectedExercises(Set<Integer> selectedIds, Set<Integer> previouslySelectedIds) {
        RecyclerView recyclerView = getView() != null ? getView().findViewById(R.id.session_exercises_recycler) : null;
        for (ExerciseTemplate ex : allExercisesCached) {
            if (selectedIds.contains(ex.getId()) && !previouslySelectedIds.contains(ex.getId())) {
                viewModel.addExercise(ex);
            }
        }
        
        if (recyclerView != null) {
            int size = viewModel.getSessionExercises().getValue() != null ? 
                    viewModel.getSessionExercises().getValue().size() : 0;
            if (size > 0) {
                recyclerView.smoothScrollToPosition(size);
            }
        }
    }
}
