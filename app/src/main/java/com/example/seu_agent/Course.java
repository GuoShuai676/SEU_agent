package com.example.seu_agent;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** 本地课程：按星期、节次和教学周描述，不上传云端。 */
@Entity(tableName = "courses")
public class Course {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String name;
    public String teacher;
    public String location;
    public int dayOfWeek;
    public int startSection;
    public int endSection;
    public int startWeek;
    public int endWeek;
    public int colorIndex;
}
