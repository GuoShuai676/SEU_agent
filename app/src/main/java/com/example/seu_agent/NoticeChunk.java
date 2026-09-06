package com.example.seu_agent;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 “我的”页面 fragment UI交互
 */
@Entity(tableName = "notice_chunks",
        indices = {@Index(value = {"url", "text"}, unique = true)})
public class NoticeChunk {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String url;         // 父资讯 url

    public String title;       // 父资讯标题（冗余，方便拼上下文）

    public String publishDate; // 父资讯日期

    public String text;        // 句子文本

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    public byte[] vector;      // float[512]
}
