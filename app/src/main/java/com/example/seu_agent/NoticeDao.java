package com.example.seu_agent;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

/**
 * 资讯 DAO：增删改查。
 * insertAll 用 REPLACE，url 重复时覆盖（天然去重）。
 * 排序统一按 publish_date 倒序：最新发布在最上面。
 */
@Dao
public interface NoticeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Notice> list);

    @Query("SELECT * FROM notices ORDER BY publish_date DESC")
    List<Notice> getAll();

    @Query("SELECT * FROM notices WHERE tag = :tag ORDER BY publish_date DESC")
    List<Notice> getByTag(String tag);

    /** 关键词检索：标题包含关键词（本地 RAG v1） */
    @Query("SELECT * FROM notices WHERE title LIKE '%' || :kw || '%' ORDER BY publish_date DESC")
    List<Notice> search(String kw);

    /** 在指定栏目内检索：标题包含关键词（动态搜索用：当前 Tab + 输入框关键词） */
    @Query("SELECT * FROM notices WHERE tag = :tag AND title LIKE '%' || :kw || '%' ORDER BY publish_date DESC")
    List<Notice> searchByTag(String tag, String kw);
}
