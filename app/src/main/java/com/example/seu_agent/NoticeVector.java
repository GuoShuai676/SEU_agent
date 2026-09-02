package com.example.seu_agent;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 资讯语义向量表（本地 RAG v2）。
 * vector 存 float[512] 的字节序列（小端）；用 url 与 notices 对应。
 */
@Entity(tableName = "notice_vectors")
public class NoticeVector {

    @NonNull
    @PrimaryKey
    public String url;

    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
    public byte[] vector;   // float[512]

    public NoticeVector() {
    }

    public NoticeVector(String url, float[] vec) {
        this.url = url;
        this.vector = toBytes(vec);
    }

    public static byte[] toBytes(float[] vec) {
        ByteBuffer buf = ByteBuffer.allocate(vec.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.asFloatBuffer().put(vec);
        return buf.array();
    }

    public static float[] toFloats(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vec = new float[bytes.length / 4];
        buf.asFloatBuffer().get(vec);
        return vec;
    }
}
