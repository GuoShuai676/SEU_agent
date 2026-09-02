package com.example.seu_agent;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 资讯句子级语义向量表（RAG v2 按句检索）。
 * 一条资讯正文按句切分后，每句存一行（含父资讯标题/日期，便于拼上下文）。
 * (url, text) 唯一：重复同步时 REPLACE 覆盖。
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
