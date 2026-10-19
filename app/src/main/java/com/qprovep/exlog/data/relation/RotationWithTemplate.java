package com.qprovep.exlog.data.relation;

import androidx.room.Embedded;
import androidx.room.Relation;

import com.qprovep.exlog.data.entity.ExerciseTemplate;
import com.qprovep.exlog.data.entity.RotationItem;

public class RotationWithTemplate {
    @Embedded
    public RotationItem rotationItem;

    @Relation(parentColumn = "exerciseTemplateId", entityColumn = "id")
    public ExerciseTemplate exerciseTemplate;
}