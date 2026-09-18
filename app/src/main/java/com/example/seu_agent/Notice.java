package com.example.seu_agent;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notices")
public class Notice {

    @NonNull
    @PrimaryKey
    public String url;

    public String tag;

    public String title;

    public String content;

    @ColumnInfo(name = "publish_date")
    public String publishDate;
}
