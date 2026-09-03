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

    @Query("SELECT * FROM courses ORDER BY id DESC")
    List<Course> getAll();

    @Query("SELECT * FROM courses WHERE dayOfWeek = :day "
            + "AND startWeek <= :week AND endWeek >= :week "
            + "AND (weekType = 0 OR (weekType = 1 AND :week % 2 = 1) "
            + "OR (weekType = 2 AND :week % 2 = 0)) ORDER BY startSection")
    List<Course> getForDay(int day, int week);
}
