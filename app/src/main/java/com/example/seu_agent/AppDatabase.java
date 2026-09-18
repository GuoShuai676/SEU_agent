package com.example.seu_agent;

import android.content.Context;

import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Notice.class, NoticeVector.class, NoticeChunk.class, Course.class,
        MemoryChunk.class, CampusTask.class}, version = 10, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;


    public abstract NoticeDao noticeDao();

    public abstract NoticeVectorDao noticeVectorDao();

    public abstract NoticeChunkDao noticeChunkDao();

    public abstract CourseDao courseDao();

    public abstract MemoryChunkDao memoryChunkDao();

    public abstract CampusTaskDao campusTaskDao();

    private static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `campus_tasks` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`title` TEXT, `note` TEXT, `dueAt` INTEGER NOT NULL, "
                    + "`completed` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, "
                    + "`source` TEXT)");
        }
    };

    public static AppDatabase get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(ctx.getApplicationContext(),
                            AppDatabase.class, "seu_agent.db")
                            .addMigrations(MIGRATION_9_10)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
