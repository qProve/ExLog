package com.qprovep.exlog.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.qprovep.exlog.data.dao.CategoryDao;
import com.qprovep.exlog.data.dao.ExerciseTemplateDao;
import com.qprovep.exlog.data.dao.SessionDao;
import com.qprovep.exlog.data.dao.SetLogDao;
import com.qprovep.exlog.data.dao.WorkoutExerciseDao;
import com.qprovep.exlog.data.dao.WorkoutTemplateDao;
import com.qprovep.exlog.data.entity.Category;
import com.qprovep.exlog.data.entity.ExerciseTemplate;
import com.qprovep.exlog.data.entity.RotationItem;
import com.qprovep.exlog.data.entity.Session;
import com.qprovep.exlog.data.entity.SetLog;
import com.qprovep.exlog.data.entity.WorkoutExercise;
import com.qprovep.exlog.data.entity.WorkoutTemplate;

@Database(entities = {
        ExerciseTemplate.class,
        WorkoutTemplate.class,
        WorkoutExercise.class,
        Session.class,
        SetLog.class,
        Category.class,
        RotationItem.class,
}, version = 5, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE workout_templates ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0");
        }
    };
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `exercise_rotations` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`workoutExerciseId` INTEGER NOT NULL, " +
                    "`exerciseTemplateId` INTEGER NOT NULL, " +
                    "`orderIndex` INTEGER NOT NULL, " +
                    "`sessionCount` INTEGER NOT NULL, " +
                    "`referenceWeight` REAL NOT NULL, " +
                    "`targetSets` INTEGER NOT NULL, " +
                    "`targetReps` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`workoutExerciseId`) REFERENCES `workout_exercises`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE, " +
                    "FOREIGN KEY(`exerciseTemplateId`) REFERENCES `exercise_templates`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_rotations_workoutExerciseId` " +
                    "ON `exercise_rotations`(`workoutExerciseId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_rotations_exerciseTemplateId` " +
                    "ON `exercise_rotations`(`exerciseTemplateId`)");
        }
    };

    public abstract ExerciseTemplateDao exerciseTemplateDao();

    public abstract WorkoutTemplateDao workoutTemplateDao();

    public abstract WorkoutExerciseDao workoutExerciseDao();

    public abstract SessionDao sessionDao();

    public abstract SetLogDao setLogDao();

    public abstract CategoryDao categoryDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "exlog_database")
                            .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
