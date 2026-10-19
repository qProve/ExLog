package com.qprovep.exlog.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.qprovep.exlog.data.entity.Session;
import com.qprovep.exlog.data.relation.SessionWithSetLogs;

import java.util.List;

@Dao
public interface SessionDao {

    @Insert
    long insert(Session session);

    @Update
    void update(Session session);

    @Delete
    void delete(Session session);

    @Query("SELECT * FROM sessions ORDER BY date DESC")
    LiveData<List<Session>> getAllSessions();

    @Query("SELECT * FROM sessions WHERE id = :id")
    Session getById(int id);

    @Query("SELECT * FROM sessions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    LiveData<List<Session>> getSessionsByDateRange(long startDate, long endDate);

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    LiveData<SessionWithSetLogs> getSessionWithSetLogs(int id);

    @Query("SELECT s.id as sessionId, w.name as workoutName, s.date, s.durationMs " +
            "FROM sessions s " +
            "INNER JOIN workout_templates w ON s.workoutTemplateId = w.id " +
            "ORDER BY s.date DESC")
    LiveData<List<SessionHistoryItem>> getSessionHistory();

    @Query("SELECT s.id as sessionId, w.name as workoutName, s.date, s.durationMs " +
            "FROM sessions s " +
            "INNER JOIN workout_templates w ON s.workoutTemplateId = w.id " +
            "WHERE s.date BETWEEN :startDate AND :endDate " +
            "ORDER BY s.date DESC")
    LiveData<List<SessionHistoryItem>> getSessionHistoryByDateRange(long startDate, long endDate);

    class SessionHistoryItem {
        public int sessionId;
        public String workoutName;
        public long date;
        public long durationMs;
    }

    @Query("SELECT * FROM sessions ORDER BY date DESC")
    List<Session> getAllSessionsSync();

    @Insert
    void insertAll(List<Session> sessions);

    @Query("SELECT COUNT(*) FROM sessions WHERE workoutTemplateId = :workoutId")
    int countSessionsForWorkoutSync(int workoutId);

    @Query("SELECT COUNT(*) FROM sessions WHERE workoutTemplateId = :workoutId AND id <= :sessionId")
    int countSessionsForWorkoutUpTo(int workoutId, int sessionId);
}
