package com.qprovep.exlog.data.relation;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.qprovep.exlog.data.entity.ExerciseTemplate;
import com.qprovep.exlog.data.entity.RotationItem;
import com.qprovep.exlog.data.entity.WorkoutExercise;

import java.util.List;

public class WorkoutExerciseWithTemplate {

    @Embedded
    public WorkoutExercise workoutExercise;

    @Relation(parentColumn = "exerciseTemplateId", entityColumn = "id")
    public ExerciseTemplate exerciseTemplate;

    @Relation(entity = RotationItem.class, parentColumn = "id", entityColumn = "workoutExerciseId")
    public List<RotationWithTemplate> rotation;
}
