package com.example.seu_agent;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface NoticeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Notice> list);

    @Query("SELECT * FROM notices ORDER BY publish_date DESC")
    List<Notice> getAll();

    @Query("SELECT * FROM notices WHERE tag = :tag ORDER BY publish_date DESC")
    List<Notice> getByTag(String tag);

    @Query("SELECT * FROM notices WHERE title LIKE '%' || :kw || '%' ORDER BY publish_date DESC")
    List<Notice> search(String kw);

}
