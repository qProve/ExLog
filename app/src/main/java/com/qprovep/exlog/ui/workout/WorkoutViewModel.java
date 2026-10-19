package com.qprovep.exlog.ui.workout;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.qprovep.exlog.data.AppDatabase;
import com.qprovep.exlog.data.dao.ExerciseTemplateDao;
import com.qprovep.exlog.data.dao.WorkoutExerciseDao;
import com.qprovep.exlog.data.dao.WorkoutTemplateDao;
import com.qprovep.exlog.data.entity.ExerciseTemplate;
import com.qprovep.exlog.data.entity.RotationItem;
import com.qprovep.exlog.data.entity.WorkoutExercise;
import com.qprovep.exlog.data.entity.WorkoutTemplate;
import com.qprovep.exlog.data.relation.WorkoutWithExercises;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WorkoutViewModel extends AndroidViewModel {

    private final WorkoutTemplateDao workoutDao;
    private final WorkoutExerciseDao workoutExerciseDao;
    private final ExerciseTemplateDao exerciseDao;
    private final LiveData<List<WorkoutTemplate>> allWorkouts;
    private final LiveData<List<ExerciseTemplate>> allExercises;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public WorkoutViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        workoutDao = db.workoutTemplateDao();
        workoutExerciseDao = db.workoutExerciseDao();
        exerciseDao = db.exerciseTemplateDao();
        allWorkouts = workoutDao.getAllWorkouts();
        allExercises = exerciseDao.getAllExercises();
    }

    public LiveData<List<WorkoutTemplate>> getAllWorkouts() {
        return allWorkouts;
    }

    public LiveData<List<ExerciseTemplate>> getAllExercises() {
        return allExercises;
    }

    public LiveData<WorkoutWithExercises> getWorkoutWithExercises(int workoutId) {
        return workoutDao.getWorkoutWithExercises(workoutId);
    }

    public void insertWorkout(WorkoutTemplate workout, List<WorkoutExercise> exercises, OnWorkoutSavedListener listener, List<List<RotationItem>> rotations) {
        executor.execute(() -> {
            long id = workoutDao.insert(workout);
            for (int i = 0; i < exercises.size(); i++) {
                WorkoutExercise we = exercises.get(i);
                we.setWorkoutTemplateId((int) id);
                long weId = workoutExerciseDao.insert(we);
                List<RotationItem> rot = rotations != null ? rotations.get(i) : null;
                if (rot != null && !rot.isEmpty()) {
                    for (RotationItem r : rot) {
                        r.setWorkoutExerciseId((int) weId);
                    }
                    workoutExerciseDao.insertRotations(rot);
                }
            }
            if (listener != null) listener.onSaved((int) id);
        });
    }

    public void updateWorkout(WorkoutTemplate workout, List<WorkoutExercise> exercises, List<List<RotationItem>> rotations) {
        executor.execute(() -> {
            workoutDao.update(workout);
            workoutExerciseDao.deleteAllForWorkout(workout.getId());
            for (int i = 0; i < exercises.size(); i++) {
                WorkoutExercise we = exercises.get(i);
                we.setWorkoutTemplateId(workout.getId());
                long weId = workoutExerciseDao.insert(we);
                List<RotationItem> rot = rotations != null ? rotations.get(i) : null;
                if (rot != null && !rot.isEmpty()) {
                    for (RotationItem r : rot) {
                        r.setWorkoutExerciseId((int) weId);
                    }
                    workoutExerciseDao.insertRotations(rot);
                }
            }
        });
    }

    public void deleteWorkout(WorkoutTemplate workout) {
        executor.execute(() -> workoutDao.delete(workout));
    }

    public interface OnWorkoutSavedListener {
        void onSaved(int workoutId);
    }
}
