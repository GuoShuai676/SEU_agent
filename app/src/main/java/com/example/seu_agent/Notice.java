package com.example.seu_agent;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 资讯实体（对应本地 Room 表 notices）。
 * 字段与云端 /api/notices 返回的 item 对应。
 * 主键 = url：同一条资讯重复同步时，REPLACE 直接覆盖，天然去重。
 */
@Entity(tableName = "notices")
public class Notice {

    @NonNull
    @PrimaryKey
    public String url;            // 详情链接：唯一标识，重复时覆盖

    public String tag;            // 教务 / 实践 / 讲座

    public String title;

    public String content;

    @ColumnInfo(name = "publish_date")
    public String publishDate;    // 如 2026-08-31
}
