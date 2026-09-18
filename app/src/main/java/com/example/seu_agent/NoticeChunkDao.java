package com.example.seu_agent;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NoticeChunkDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<NoticeChunk> list);

    @Query("SELECT * FROM notice_chunks")
    List<NoticeChunk> getAll();

    @Query("DELETE FROM notice_chunks WHERE url NOT IN (SELECT url FROM notices)")
    void deleteOrphans();

    @Query("SELECT COUNT(*) FROM notice_chunks")
    int count();
}
