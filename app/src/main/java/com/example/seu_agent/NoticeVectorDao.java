package com.example.seu_agent;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NoticeVectorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<NoticeVector> list);

    @Query("SELECT * FROM notice_vectors")
    List<NoticeVector> getAll();

    @Query("SELECT COUNT(*) FROM notice_vectors")
    int count();
}
