package com.example.seu_agent;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "memory_chunks", indices = {@Index("turnId")})
public class MemoryChunk {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long turnId;

    public int chunkIndex;

    public String userMessage;

    public String assistantMessage;
//
    public long createdAt;

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    public byte[] vector;
}
