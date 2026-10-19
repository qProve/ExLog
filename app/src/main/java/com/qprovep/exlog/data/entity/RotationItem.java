package com.qprovep.exlog.data.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "exercise_rotations", foreignKeys = {
        @ForeignKey(entity = WorkoutExercise.class, parentColumns = "id", childColumns = "workoutExerciseId", onDelete = ForeignKey.CASCADE),
        @ForeignKey(entity = ExerciseTemplate.class, parentColumns = "id", childColumns = "exerciseTemplateId", onDelete = ForeignKey.CASCADE)
}, indices = {
        @Index("workoutExerciseId"),
        @Index("exerciseTemplateId")
})
public class RotationItem {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int workoutExerciseId;
    private int exerciseTemplateId;
    private int orderIndex;
    private int sessionCount;
    private double referenceWeight;
    private int targetSets;
    private int targetReps;

    public RotationItem(int workoutExerciseId, int exerciseTemplateId, int orderIndex,
                        int sessionCount, double referenceWeight, int targetSets, int targetReps) {
        this.workoutExerciseId = workoutExerciseId;
        this.exerciseTemplateId = exerciseTemplateId;
        this.orderIndex = orderIndex;
        this.sessionCount = sessionCount;
        this.referenceWeight = referenceWeight;
        this.targetSets = targetSets;
        this.targetReps = targetReps;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getWorkoutExerciseId() { return workoutExerciseId; }
    public void setWorkoutExerciseId(int workoutExerciseId) { this.workoutExerciseId = workoutExerciseId; }
    public int getExerciseTemplateId() { return exerciseTemplateId; }
    public void setExerciseTemplateId(int exerciseTemplateId) { this.exerciseTemplateId = exerciseTemplateId; }
    public int getOrderIndex() { return orderIndex; }
    public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
    public int getSessionCount() { return sessionCount; }
    public void setSessionCount(int sessionCount) { this.sessionCount = sessionCount; }
    public double getReferenceWeight() { return referenceWeight; }
    public void setReferenceWeight(double referenceWeight) { this.referenceWeight = referenceWeight; }
    public int getTargetSets() { return targetSets; }
    public void setTargetSets(int targetSets) { this.targetSets = targetSets; }
    public int getTargetReps() { return targetReps; }
    public void setTargetReps(int targetReps) { this.targetReps = targetReps; }
}