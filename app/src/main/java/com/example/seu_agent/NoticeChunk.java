package com.example.seu_agent;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "notice_chunks",
        indices = {@Index(value = {"url", "text"}, unique = true)})
public class NoticeChunk {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String url;

    public String title;

    public String publishDate;

    public String text;

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    public byte[] vector;
}
