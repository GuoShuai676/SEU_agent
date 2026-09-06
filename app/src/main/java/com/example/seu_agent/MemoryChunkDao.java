package com.example.seu_agent;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface MemoryChunkDao {

    @Insert
    void insertAll(List<MemoryChunk> memories);

    @Query("SELECT * FROM memory_chunks ORDER BY createdAt DESC")
    List<MemoryChunk> getAllNewestFirst();

    @Query("DELETE FROM memory_chunks WHERE turnId NOT IN "
            + "(SELECT turnId FROM memory_chunks GROUP BY turnId "
            + "ORDER BY MAX(createdAt) DESC LIMIT :maxTurns)")
    void keepLatestTurns(int maxTurns);

    @Query("DELETE FROM memory_chunks")
    void clear();
}
