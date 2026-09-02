package com.example.seu_agent;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface CourseDao {
    @Insert
    long insert(Course course);

    @Delete
    void delete(Course course);

    @Query("SELECT * FROM courses WHERE startWeek <= :week AND endWeek >= :week "
            + "ORDER BY dayOfWeek, startSection")
    List<Course> getForWeek(int week);

    @Query("SELECT COUNT(*) FROM courses WHERE dayOfWeek = :day "
            + "AND startWeek <= :endWeek AND endWeek >= :startWeek "
            + "AND startSection <= :endSection AND endSection >= :startSection")
    int countConflicts(int day, int startSection, int endSection,
                       int startWeek, int endWeek);
}
