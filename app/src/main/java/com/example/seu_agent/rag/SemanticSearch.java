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
import java.util.concurrent.atomic.AtomicBoolean;

public class SemanticSearch {
    public static final float SCORE_THRESHOLD = 0.5f;
    private static final AtomicBoolean INDEXING = new AtomicBoolean(false);

    public static List<NoticeChunk> topChunks(Context ctx, String question, int k) {
        try {
            List<NoticeChunk> all = AppDatabase.get(ctx).noticeChunkDao().getAll();
            if (all.isEmpty()) {
                warmUpAsync(ctx);
                return all;
            }

            if (INDEXING.get()) return new ArrayList<>();

            float[] q = BgeEmbedder.embed(ctx, question);
            if (q == null) return new ArrayList<>();

            float[] scores = new float[all.size()];
            for (int i = 0; i < all.size(); i++) {
                float[] v = NoticeVector.toFloats(all.get(i).vector);
                scores[i] = (v == null || v.length != BgeEmbedder.DIM) ? -1f : dot(q, v);
            }
            Integer[] idx = new Integer[all.size()];
            for (int i = 0; i < idx.length; i++) idx[i] = i;
            Arrays.sort(idx, (a, b) -> Float.compare(scores[b], scores[a]));

            if (scores[idx[0]] < SCORE_THRESHOLD) return new ArrayList<>();

            List<NoticeChunk> result = new ArrayList<>();
            for (int i = 0; i < Math.min(k, idx.length); i++) {
                if (scores[idx[i]] < SCORE_THRESHOLD) break;
                result.add(all.get(idx[i]));
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void warmUp(Context ctx) {
        if (!INDEXING.compareAndSet(false, true)) return;
        try {
            ensureChunksInternal(ctx);
        } catch (Exception ignored) {
        } finally {
            INDEXING.set(false);
        }
    }

    private static void warmUpAsync(Context ctx) {
        if (INDEXING.get()) return;
        Context appContext = ctx.getApplicationContext();
        new Thread(() -> warmUp(appContext), "notice-vector-index").start();
    }

    private static boolean ensureChunksInternal(Context ctx) {
        try {
            List<Notice> notices = AppDatabase.get(ctx).noticeDao().getAll();
            if (notices.isEmpty()) {
                AppDatabase.get(ctx).noticeChunkDao().deleteOrphans();
                return true;
            }

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
