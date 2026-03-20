package com.qprovep.exlog.ui.session;

import android.app.Application;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.qprovep.exlog.data.AppDatabase;
import com.qprovep.exlog.data.dao.SessionDao;
import com.qprovep.exlog.data.dao.SetLogDao;
import com.qprovep.exlog.data.dao.WorkoutTemplateDao;
import com.qprovep.exlog.data.entity.ExerciseTemplate;
import com.qprovep.exlog.data.entity.Session;
import com.qprovep.exlog.data.entity.SetLog;
import com.qprovep.exlog.data.entity.WorkoutTemplate;
import com.qprovep.exlog.data.relation.WorkoutWithExercises;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SessionViewModel extends AndroidViewModel {

    private final SessionDao sessionDao;
    private final SetLogDao setLogDao;
    private final WorkoutTemplateDao workoutTemplateDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final MutableLiveData<WorkoutTemplate> currentWorkout = new MutableLiveData<>();
    private final MutableLiveData<List<SessionExerciseEntry>> sessionExercises = new MutableLiveData<>(
            new ArrayList<>());

    private long startTimeMillis = 0;
    private long accumulatedMs = 0;
    private final MutableLiveData<Long> elapsedTime = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> timerPaused = new MutableLiveData<>(false);
    private boolean isTimerRunning = false;
    private boolean isPaused = false;
    private Thread timerThread;

    public SessionViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        sessionDao = db.sessionDao();
        setLogDao = db.setLogDao();
        workoutTemplateDao = db.workoutTemplateDao();
    }

    public LiveData<WorkoutTemplate> getCurrentWorkout() {
        return currentWorkout;
    }

    public LiveData<List<SessionExerciseEntry>> getSessionExercises() {
        return sessionExercises;
    }

    public LiveData<Long> getElapsedTime() {
        return elapsedTime;
    }

    public LiveData<Boolean> isTimerPaused() {
        return timerPaused;
    }

    public void startSession(int workoutId) {
        if (isTimerRunning)
            return;

        executor.execute(() -> {
            WorkoutWithExercises data = workoutTemplateDao.getWorkoutWithExercisesSync(workoutId);
            if (data == null)
                return;

            currentWorkout.postValue(data.workoutTemplate);

            List<SessionExerciseEntry> entries = new ArrayList<>();
            for (var we : data.exercises) {
                List<SetEntry> sets = new ArrayList<>();
                for (int i = 0; i < we.workoutExercise.getTargetSets(); i++) {
                    sets.add(new SetEntry(i + 1, (float) we.workoutExercise.getReferenceWeight(),
                            we.workoutExercise.getTargetReps()));
                }
                entries.add(new SessionExerciseEntry(we.exerciseTemplate, sets));
            }
            sessionExercises.postValue(entries);

            accumulatedMs = 0;
            isPaused = false;
            timerPaused.postValue(false);
            startTimeMillis = SystemClock.elapsedRealtime();
            isTimerRunning = true;
            startTimerThread();
        });
    }

    public void togglePause() {
        if (!isTimerRunning)
            return;

        if (isPaused) {
            startTimeMillis = SystemClock.elapsedRealtime();
            isPaused = false;
            timerPaused.postValue(false);
            startTimerThread();
        } else {
            accumulatedMs += SystemClock.elapsedRealtime() - startTimeMillis;
            isPaused = true;
            timerPaused.postValue(true);
            if (timerThread != null) {
                timerThread.interrupt();
            }
        }
    }

    private void startTimerThread() {
        timerThread = new Thread(() -> {
            while (isTimerRunning && !isPaused) {
                long elapsed = accumulatedMs + (SystemClock.elapsedRealtime() - startTimeMillis);
                elapsedTime.postValue(elapsed);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        timerThread.start();
    }

    public void updateSet(int exerciseIndex, int setIndex, float weight, int reps, boolean isCompleted) {
        List<SessionExerciseEntry> current = sessionExercises.getValue();
        if (current == null)
            return;

        SetEntry set = current.get(exerciseIndex).sets.get(setIndex);
        set.weight = weight;
        set.reps = reps;
        set.isCompleted = isCompleted;
    }

    public void addExercise(ExerciseTemplate exercise) {
        executor.execute(() -> {
            List<SetLog> lastLogs = setLogDao.getLastSetLogsForExercise(exercise.getId());
            List<SetEntry> initialSets = new ArrayList<>();

            if (lastLogs != null && !lastLogs.isEmpty()) {
                for (SetLog log : lastLogs) {
                    initialSets.add(new SetEntry(log.getSetNumber(), (float) log.getWeight(), log.getReps()));
                }
            } else {
                initialSets.add(new SetEntry(1, 0f, 0));
            }

            List<SessionExerciseEntry> current = sessionExercises.getValue();
            if (current != null) {
                current.add(new SessionExerciseEntry(exercise, initialSets));
                sessionExercises.postValue(current);
            }
        });
    }

    public void removeExercise(int exerciseIndex) {
        List<SessionExerciseEntry> current = sessionExercises.getValue();
        if (current == null || exerciseIndex < 0 || exerciseIndex >= current.size()) return;

        current.remove(exerciseIndex);
        sessionExercises.postValue(current);
    }

    public void swapExercises(int fromPosition, int toPosition) {
        List<SessionExerciseEntry> current = sessionExercises.getValue();
        if (current == null || fromPosition < 0 || toPosition < 0 || 
            fromPosition >= current.size() || toPosition >= current.size()) return;

        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                java.util.Collections.swap(current, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                java.util.Collections.swap(current, i, i - 1);
            }
        }
    }

    public void addSet(int exerciseIndex) {
        List<SessionExerciseEntry> current = sessionExercises.getValue();
        if (current == null)
            return;

        SessionExerciseEntry entry = current.get(exerciseIndex);
        SetEntry lastSet = entry.sets.get(entry.sets.size() - 1);
        entry.sets.add(new SetEntry(entry.sets.size() + 1, lastSet.weight, lastSet.reps));
        sessionExercises.postValue(current);
    }

    public void removeSet(int exerciseIndex) {
        List<SessionExerciseEntry> current = sessionExercises.getValue();
        if (current == null)
            return;

        SessionExerciseEntry entry = current.get(exerciseIndex);
        if (entry.sets.size() <= 1)
            return;

        entry.sets.remove(entry.sets.size() - 1);
        for (int i = 0; i < entry.sets.size(); i++) {
            entry.sets.get(i).setNumber = i + 1;
        }
        sessionExercises.postValue(current);
    }

    public void finishSession(String notes) {
        if (!isTimerRunning)
            return;

        isTimerRunning = false;
        if (timerThread != null) {
            timerThread.interrupt();
        }

        long durationMs = calculateDuration();
        WorkoutTemplate template = currentWorkout.getValue();
        List<SessionExerciseEntry> entries = sessionExercises.getValue();

        if (template == null || entries == null)
            return;

        executor.execute(() -> {
            saveSessionInternal(template, entries, durationMs, notes);

            isPaused = false;
            accumulatedMs = 0;
            currentWorkout.postValue(null);
            sessionExercises.postValue(new ArrayList<>());
            elapsedTime.postValue(0L);
            timerPaused.postValue(false);
        });
    }

    public void finishSessionSync(String notes) {
        if (!isTimerRunning && accumulatedMs == 0)
            return;

        isTimerRunning = false;
        if (timerThread != null) {
            timerThread.interrupt();
        }

        long durationMs = calculateDuration();
        WorkoutTemplate template = currentWorkout.getValue();
        List<SessionExerciseEntry> entries = sessionExercises.getValue();

        if (template == null || entries == null)
            return;

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

        executor.execute(() -> {
            try {
                saveSessionInternal(template, entries, durationMs, notes);
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private long calculateDuration() {
        if (isPaused || startTimeMillis == 0) {
            return accumulatedMs;
        }
        return accumulatedMs + (android.os.SystemClock.elapsedRealtime() - startTimeMillis);
    }

    private void saveSessionInternal(WorkoutTemplate template, List<SessionExerciseEntry> entries, long durationMs, String notes) {
        Session session = new Session(template.getId(), new java.util.Date().getTime(), durationMs, notes);
        long sessionId = sessionDao.insert(session);

        List<SetLog> logsToSave = new ArrayList<>();
        for (SessionExerciseEntry entry : entries) {
            for (SetEntry set : entry.sets) {
                if (set.isCompleted) {
                    logsToSave.add(new SetLog((int) sessionId, entry.exercise.getId(), set.setNumber, set.reps, set.weight));
                }
            }
        }

        if (!logsToSave.isEmpty()) {
            setLogDao.insertAll(logsToSave);
        }
    }

    public void discardSession() {
        isTimerRunning = false;
        isPaused = false;
        if (timerThread != null) {
            timerThread.interrupt();
        }
        accumulatedMs = 0;
        currentWorkout.postValue(null);
        sessionExercises.postValue(new ArrayList<>());
        elapsedTime.postValue(0L);
        timerPaused.postValue(false);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (isTimerRunning || accumulatedMs > 0) {
            finishSessionSync("Auto-saved on exit");
        }
        isTimerRunning = false;
        if (timerThread != null)
            timerThread.interrupt();
    }

    public static class SessionExerciseEntry {
        public ExerciseTemplate exercise;
        public List<SetEntry> sets;

        public SessionExerciseEntry(ExerciseTemplate exercise, List<SetEntry> sets) {
            this.exercise = exercise;
            this.sets = sets;
        }
    }

    public static class SetEntry {
        public int setNumber;
        public float weight;
        public int reps;
        public boolean isCompleted;

        public SetEntry(int setNumber, float weight, int reps) {
            this.setNumber = setNumber;
            this.weight = weight;
            this.reps = reps;
            this.isCompleted = false;
        }
    }
}
