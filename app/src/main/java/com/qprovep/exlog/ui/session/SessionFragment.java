package com.qprovep.exlog.ui.session;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import androidx.viewpager2.widget.ViewPager2;

import android.widget.ImageButton;

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SessionFragment extends Fragment {

    private SessionViewModel viewModel;
    private SessionPagerAdapter adapter;
    private ViewPager2 viewPager;
    private LinearLayout dotsContainer;
    private ImageButton arrowPrev;
    private ImageButton arrowNext;
    private TextView emptyText;
    private TextView timerText;

    private List<ExerciseTemplate> allExercisesCached;
    private final List<String> categoriesCached = new ArrayList<>();
    private boolean pendingScrollToLast = false;

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
        viewPager = view.findViewById(R.id.session_viewpager);
        dotsContainer = view.findViewById(R.id.session_dots);
        emptyText = view.findViewById(R.id.session_empty_text);
        arrowPrev = view.findViewById(R.id.session_arrow_prev);
        arrowNext = view.findViewById(R.id.session_arrow_next);
        MaterialButton finishBtn = view.findViewById(R.id.btn_finish_session);
        MaterialButton pauseResumeBtn = view.findViewById(R.id.btn_pause_resume);

        adapter = new SessionPagerAdapter(viewModel);
        adapter.setAutoAdvanceListener((fromIndex, toIndex) ->
                viewPager.setCurrentItem(toIndex, true));
        adapter.setOnCompletionChanged(this::updateDots);
        viewPager.setAdapter(adapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateDots();
            }
        });

        arrowPrev.setOnClickListener(v -> goToExercise(-1));
        arrowNext.setOnClickListener(v -> goToExercise(1));

        toolbar.inflateMenu(R.menu.menu_session);
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_add_exercise) {
                showExercisePicker();
                return true;
            } else if (id == R.id.action_reorder_exercises) {
                showReorderDialog();
                return true;
            } else if (id == R.id.action_delete_exercise) {
                confirmDeleteCurrentExercise();
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
                boolean empty = entries.isEmpty();
                emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
                viewPager.setVisibility(empty ? View.GONE : View.VISIBLE);
                boolean multi = entries.size() > 1;
                arrowPrev.setVisibility(multi ? View.VISIBLE : View.GONE);
                arrowNext.setVisibility(multi ? View.VISIBLE : View.GONE);
                rebuildDots(entries.size());
                int position = viewPager.getCurrentItem();
                if (position >= entries.size()) {
                    viewPager.setCurrentItem(Math.max(0, entries.size() - 1), false);
                } else if (pendingScrollToLast && !entries.isEmpty()) {
                    pendingScrollToLast = false;
                    viewPager.setCurrentItem(entries.size() - 1, true);
                }
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

    private void goToExercise(int delta) {
        int count = adapter.getItemCount();
        if (count < 2) return;

        int position = viewPager.getCurrentItem();
        int target = (position + delta + count) % count;
        boolean wraps = (delta > 0 && target == 0) || (delta < 0 && target == count - 1);
        viewPager.setCurrentItem(target, !wraps);
    }

    private void rebuildDots(int count) {
        dotsContainer.removeAllViews();
        int size = getResources().getDimensionPixelSize(R.dimen.dot_size);
        int margin = getResources().getDimensionPixelSize(R.dimen.dot_margin);
        for (int i = 0; i < count; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(margin, margin, margin, margin);
            dot.setLayoutParams(params);
            dotsContainer.addView(dot);
        }
        updateDots();
    }

    private void updateDots() {
        int count = dotsContainer.getChildCount();
        int selected = viewPager.getCurrentItem();
        float inactiveAlpha = getResources().getFloat(R.dimen.dot_inactive_alpha);
        for (int i = 0; i < count; i++) {
            View dot = dotsContainer.getChildAt(i);
            if (adapter.isExerciseDone(i)) {
                dot.setBackgroundResource(R.drawable.dot_done);
                dot.setAlpha(1f);
            } else {
                dot.setBackgroundResource(R.drawable.dot_selected);
                dot.setAlpha(i == selected ? 1f : inactiveAlpha);
            }
        }
    }

    private void confirmDeleteCurrentExercise() {
        List<SessionExerciseEntry> entries = viewModel.getSessionExercises().getValue();
        int position = viewPager.getCurrentItem();
        if (entries == null || position < 0 || position >= entries.size()) {
            Toast.makeText(requireContext(), "No exercise to delete", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = entries.get(position).exercise.getName();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.delete_exercise_title))
                .setMessage(getString(R.string.delete_exercise_message) + "\n\n" + name)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    viewModel.removeExercise(position);
                    List<SessionExerciseEntry> remaining = viewModel.getSessionExercises().getValue();
                    if (remaining != null && !remaining.isEmpty()) {
                        int target = Math.min(position, remaining.size() - 1);
                        viewPager.setCurrentItem(target, false);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showReorderDialog() {
        List<SessionExerciseEntry> currentEntries = viewModel.getSessionExercises().getValue();
        if (currentEntries == null || currentEntries.isEmpty()) {
            Toast.makeText(requireContext(), "No exercises to reorder", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_reorder_exercises, null);

        RecyclerView reorderRecycler = dialogView.findViewById(R.id.reorder_recycler);
        reorderRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        ReorderExerciseAdapter reorderAdapter = new ReorderExerciseAdapter();
        reorderAdapter.setEntries(currentEntries);
        reorderRecycler.setAdapter(reorderAdapter);

        ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                    @NonNull RecyclerView.ViewHolder viewHolder,
                    @NonNull RecyclerView.ViewHolder target) {
                reorderAdapter.onItemMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        touchHelper.attachToRecyclerView(reorderRecycler);
        reorderAdapter.setDragStartListener(touchHelper::startDrag);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reorder_exercises)
                .setView(dialogView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    List<SessionExerciseEntry> reordered = reorderAdapter.getEntries();
                    SessionExerciseEntry current = null;
                    List<SessionExerciseEntry> entries = viewModel.getSessionExercises().getValue();
                    int position = viewPager.getCurrentItem();
                    if (entries != null && position >= 0 && position < entries.size()) {
                        current = entries.get(position);
                    }
                    viewModel.reorderExercises(reordered);
                    if (current != null) {
                        int newIndex = reordered.indexOf(current);
                        if (newIndex != -1) {
                            viewPager.setCurrentItem(newIndex, false);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
        boolean added = false;
        for (ExerciseTemplate ex : allExercisesCached) {
            if (selectedIds.contains(ex.getId()) && !previouslySelectedIds.contains(ex.getId())) {
                viewModel.addExercise(ex);
                added = true;
            }
        }
        if (added) {
            pendingScrollToLast = true;
        }
    }
}
