package com.qprovep.exlog.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.qprovep.exlog.data.entity.RotationItem;
import com.qprovep.exlog.data.entity.WorkoutExercise;

import java.util.List;

@Dao
public interface WorkoutExerciseDao {

    @Insert
    long insert(WorkoutExercise workoutExercise);

    @Insert
    void insertAll(List<WorkoutExercise> workoutExercises);

    @Insert
    void insertRotations(List<RotationItem> items);

    @Update
    void update(WorkoutExercise workoutExercise);

    @Delete
    void delete(WorkoutExercise workoutExercise);

    @Query("DELETE FROM workout_exercises WHERE workoutTemplateId = :workoutTemplateId")
    void deleteAllForWorkout(int workoutTemplateId);

    @Query("SELECT * FROM workout_exercises WHERE workoutTemplateId = :workoutTemplateId ORDER BY orderIndex ASC")
    List<WorkoutExercise> getExercisesForWorkout(int workoutTemplateId);

    @Query("SELECT * FROM workout_exercises ORDER BY workoutTemplateId, orderIndex ASC")
    List<WorkoutExercise> getAllWorkoutExercisesSync();

    @Query("SELECT * FROM exercise_rotations WHERE workoutExerciseId = :workoutExerciseId ORDER BY orderIndex ASC")
    List<RotationItem> getRotationsForWorkoutExercise(int workoutExerciseId);
}
