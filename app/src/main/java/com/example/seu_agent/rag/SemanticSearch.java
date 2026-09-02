package com.example.seu_agent.rag;

import android.content.Context;

import com.example.seu_agent.AppDatabase;
import com.example.seu_agent.Notice;
import com.example.seu_agent.NoticeChunk;
import com.example.seu_agent.NoticeVector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地语义检索（RAG v2，按句）：
 * ① 正文按句切分（C++ splitSentences），每句算向量存 Room（标题+句子一起嵌入，带上下文）；
 * ② 提问向量化，与所有句子向量做余弦相似度，取 top-k；
 * ③ 低于相关阈值 → 视为无关，返回空列表（由调用方决定不注入）。
 */
public class SemanticSearch {

    /**
     * 相关性门控阈值（句级分数通常比整篇低，用 0.5）：
     * 低于阈值视为"提问与本地资讯无关"，不注入上下文（避免模型被无关内容带偏）。
     */
    public static final float SCORE_THRESHOLD = 0.5f;

    /** 按句检索 top-k（后台线程调用）；无关提问返回空列表 */
    public static List<NoticeChunk> topChunks(Context ctx, String question, int k) {
        try {
            if (!ensureChunks(ctx)) return new ArrayList<>();
            List<NoticeChunk> all = AppDatabase.get(ctx).noticeChunkDao().getAll();
            if (all.isEmpty()) return all;

            float[] q = BgeEmbedder.embed(ctx, question);
            if (q == null) return new ArrayList<>();

            // 算余弦（两边已归一化 → 点积即余弦）
            float[] scores = new float[all.size()];
            for (int i = 0; i < all.size(); i++) {
                float[] v = NoticeVector.toFloats(all.get(i).vector);
                scores[i] = (v == null || v.length != BgeEmbedder.DIM) ? -1f : dot(q, v);
            }
            Integer[] idx = new Integer[all.size()];
            for (int i = 0; i < idx.length; i++) idx[i] = i;
            Arrays.sort(idx, (a, b) -> Float.compare(scores[b], scores[a]));

            // 门控：最高分都低于阈值 → 无关
            if (scores[idx[0]] < SCORE_THRESHOLD) return new ArrayList<>();

            List<NoticeChunk> result = new ArrayList<>();
            for (int i = 0; i < Math.min(k, idx.length); i++) {
                if (scores[idx[i]] < 0) break;
                result.add(all.get(idx[i]));
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** 预加载模型 + 补齐全部句子向量（进入小助手页时后台预热） */
    public static void warmUp(Context ctx) {
        try {
            ensureChunks(ctx);
        } catch (Exception ignored) {
        }
    }

    /**
     * 重建/补齐句子索引：对每条资讯正文按句切分，缺向量就嵌入（标题 + "：" + 句子）入库。
     * 增量执行：已有 (url, text) 的句子跳过；清理已失效资讯的句子。
     */
    public static boolean ensureChunks(Context ctx) {
        try {
            List<Notice> notices = AppDatabase.get(ctx).noticeDao().getAll();
            if (notices.isEmpty()) return true;

            // 已有句子 → 跳过
            Map<String, byte[]> existing = new HashMap<>();
            for (NoticeChunk c : AppDatabase.get(ctx).noticeChunkDao().getAll()) {
                existing.put(c.url + "\u0000" + c.text, c.vector);
            }

            List<NoticeChunk> toAdd = new ArrayList<>();
            for (Notice n : notices) {
                String content = n.content == null ? "" : n.content;
                if (content.trim().isEmpty()) continue;
                List<String> sents = BgeEmbedder.splitSentences(ctx, content);
                for (String s : sents) {
                    String key = n.url + "\u0000" + s;
                    if (existing.containsKey(key)) continue;
                    // 标题 + 句子一起嵌入：短句也能带上下文
                    String embedText = (n.title == null ? "" : n.title) + "：" + s;
                    float[] vec = BgeEmbedder.embed(ctx, embedText);
                    if (vec == null) continue;
                    NoticeChunk c = new NoticeChunk();
                    c.url = n.url;
                    c.title = n.title;
                    c.publishDate = n.publishDate;
                    c.text = s;
                    c.vector = NoticeVector.toBytes(vec);
                    toAdd.add(c);
                }
            }
            if (!toAdd.isEmpty()) {
                AppDatabase.get(ctx).noticeChunkDao().upsertAll(toAdd);
            }
            AppDatabase.get(ctx).noticeChunkDao().deleteOrphans();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static float dot(float[] a, float[] b) {
        float s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
