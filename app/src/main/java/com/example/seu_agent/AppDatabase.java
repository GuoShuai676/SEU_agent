package com.example.seu_agent;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * 本地数据库入口。
 * room,用于存储资讯信息和向量表，便于rag检索
 */
@Database(entities = {Notice.class, NoticeVector.class, NoticeChunk.class, Course.class},
        version = 7, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;


    public abstract NoticeDao noticeDao();

    public abstract NoticeVectorDao noticeVectorDao();

    public abstract NoticeChunkDao noticeChunkDao();

    public abstract CourseDao courseDao();

    public static AppDatabase get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(ctx.getApplicationContext(),
                            AppDatabase.class, "seu_agent.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
