package com.example.seu_agent;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;

/**
 * 本地数据库入口（单例）。
 * 存：云端同步的资讯（notices）、资讯语义向量（notice_vectors）、句子级向量（notice_chunks）。
 * v2：主键由自增 id 改为 url（否则重复同步会堆重复数据）；
 * v3：新增 notice_vectors 表（RAG v2 语义向量）；
 * v4：新增 notice_chunks 表（按句检索）。
 *     开发阶段用破坏性迁移，数据会从云端重新拉取。
 */
@Database(entities = {Notice.class, NoticeVector.class, NoticeChunk.class, Course.class},
        version = 5, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    /** v5 只新增本地课表，升级时保留已经缓存的资讯和 RAG 向量。 */
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `courses` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT, `teacher` TEXT, `location` TEXT, "
                    + "`dayOfWeek` INTEGER NOT NULL, `startSection` INTEGER NOT NULL, "
                    + "`endSection` INTEGER NOT NULL, `startWeek` INTEGER NOT NULL, "
                    + "`endWeek` INTEGER NOT NULL, `colorIndex` INTEGER NOT NULL)");
        }
    };

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
                            .addMigrations(MIGRATION_4_5)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
