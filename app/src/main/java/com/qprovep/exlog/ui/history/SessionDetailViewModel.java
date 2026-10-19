package com.qprovep.exlog.ui.history;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.qprovep.exlog.data.AppDatabase;
import com.qprovep.exlog.data.dao.ExerciseTemplateDao;
import com.qprovep.exlog.data.dao.SessionDao;
import com.qprovep.exlog.data.dao.WorkoutTemplateDao;
import com.qprovep.exlog.data.entity.ExerciseTemplate;
import com.qprovep.exlog.data.entity.SetLog;
import com.qprovep.exlog.data.entity.WorkoutTemplate;
import com.qprovep.exlog.data.relation.SessionWithSetLogs;
import com.qprovep.exlog.data.relation.RotationWithTemplate;
import com.qprovep.exlog.data.relation.WorkoutExerciseWithTemplate;
import com.qprovep.exlog.ui.workout.WorkoutExerciseEditAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Collections;
import java.util.Comparator;

public class SessionDetailViewModel extends AndroidViewModel {

    private final SessionDao sessionDao;
    private final WorkoutTemplateDao workoutDao;
    private final ExerciseTemplateDao exerciseDao;
    private final MutableLiveData<Integer> sessionIdInput = new MutableLiveData<>();
    private final LiveData<SessionWithSetLogs> sessionData;

    private final MutableLiveData<WorkoutTemplate> workoutTemplate = new MutableLiveData<>();
    private final MutableLiveData<List<DetailExerciseEntry>> exercisesData = new MutableLiveData<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SessionDetailViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        sessionDao = db.sessionDao();
        workoutDao = db.workoutTemplateDao();
        exerciseDao = db.exerciseTemplateDao();

        sessionData = Transformations.switchMap(sessionIdInput, id -> {
            return sessionDao.getSessionWithSetLogs(id);
        });

        sessionData.observeForever(sessionWithLogs -> {
            if (sessionWithLogs != null) {
                executor.execute(() -> {
                    Integer workoutId = sessionWithLogs.session.getWorkoutTemplateId();
                    if (workoutId == null)
                        return;

                    com.qprovep.exlog.data.relation.WorkoutWithExercises workoutData = workoutDao.getWorkoutWithExercisesSync(workoutId);

                    if (workoutData != null) {
                        workoutTemplate.postValue(workoutData.workoutTemplate);

                        int sessionsDone = Math.max(0, sessionDao.countSessionsForWorkoutUpTo(workoutId, sessionWithLogs.session.getId()) - 1);

                        Map<Integer, DetailExerciseEntry> map = new HashMap<>();
                        List<DetailExerciseEntry> orderedList = new ArrayList<>();

                        List<WorkoutExerciseWithTemplate> sorted = new ArrayList<>(workoutData.exercises);
                        Collections.sort(sorted, Comparator.comparingInt(w -> w.workoutExercise.getOrderIndex()));

                        for (WorkoutExerciseWithTemplate we : sorted) {
                            ExerciseTemplate ex = we.exerciseTemplate;
                            int targetSets = we.workoutExercise.getTargetSets();

                            if (we.rotation != null && we.rotation.size() > 1) {
                                WorkoutExerciseEditAdapter.ExerciseEntry tmp = new WorkoutExerciseEditAdapter.ExerciseEntry(
                                        ex,
                                        we.workoutExercise.getReferenceWeight(), targetSets,
                                        we.workoutExercise.getTargetReps()
                                );
                                List<RotationWithTemplate> rotSorted = new ArrayList<>(we.rotation);
                                Collections.sort(rotSorted, Comparator.comparingInt(
                                        r -> r.rotationItem.getOrderIndex()));
                                for (RotationWithTemplate r : rotSorted) {
                                    tmp.rotation.add(new WorkoutExerciseEditAdapter.RotationItem(
                                            r.exerciseTemplate,
                                            r.rotationItem.getSessionCount(),
                                            r.rotationItem.getReferenceWeight(),
                                            r.rotationItem.getTargetSets(),
                                            r.rotationItem.getTargetReps()));
                                }
                                WorkoutExerciseEditAdapter.RotationItem active = WorkoutExerciseEditAdapter.resolveActive(tmp, sessionsDone);
                                ex = active.exercise;
                                targetSets = active.targetSets;
                            }

                            if (ex != null && !map.containsKey(ex.getId())) {
                                DetailExerciseEntry entry = new DetailExerciseEntry(ex, targetSets);
                                map.put(ex.getId(), entry);
                                orderedList.add(entry);
                            }
                        }

                        for (SetLog set : sessionWithLogs.setLogs) {
                            int exId = set.getExerciseTemplateId();
                            DetailExerciseEntry entry = map.get(exId);
                            if (entry == null) {
                                ExerciseTemplate ex = exerciseDao.getById(exId);
                                if (ex != null) {
                                    entry = new DetailExerciseEntry(ex, 0);
                                    map.put(exId, entry);
                                    orderedList.add(entry);
                                }
                            }
                            if (entry != null) {
                                entry.sets.add(set);
                            }
                        }

                        for (DetailExerciseEntry entry : orderedList) {
                            entry.sets.sort((s1, s2) -> Integer.compare(s1.getSetNumber(), s2.getSetNumber()));

                            if (entry.targetSets > entry.sets.size()) {
                                int existingCount = entry.sets.size();
                                for (int i = existingCount + 1; i <= entry.targetSets; i++) {
                                    SetLog skippedSet = new SetLog(0, 0, i, -1, -1.0);
                                    entry.sets.add(skippedSet);
                                }
                            }
                        }

                        exercisesData.postValue(orderedList);
                    }
                });
            }
        });
    }

    public void setSessionId(int id) {
        if (sessionIdInput.getValue() == null || sessionIdInput.getValue() != id) {
            sessionIdInput.setValue(id);
        }
    }

    public LiveData<SessionWithSetLogs> getSessionData() {
        return sessionData;
    }

    public LiveData<WorkoutTemplate> getWorkoutTemplate() {
        return workoutTemplate;
    }

    public LiveData<List<DetailExerciseEntry>> getExercisesData() {
        return exercisesData;
    }

    public static class DetailExerciseEntry {
        public final ExerciseTemplate exercise;
        public final int targetSets;
        public final List<SetLog> sets = new ArrayList<>();

        public DetailExerciseEntry(ExerciseTemplate exercise, int targetSets) {
            this.exercise = exercise;
            this.targetSets = targetSets;
        }
    }
}
