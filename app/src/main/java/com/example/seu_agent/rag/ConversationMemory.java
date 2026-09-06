package com.example.seu_agent.rag;

import android.content.Context;

import com.example.seu_agent.AppDatabase;
import com.example.seu_agent.MemoryChunk;
import com.example.seu_agent.NoticeVector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ConversationMemory {

    private static final float SCORE_THRESHOLD = 0.42f;
    private static final int MAX_TURN_COUNT = 500;
    private static final int CHUNK_LENGTH = 225;
    private static final int MAX_CHUNKS_PER_TURN = 4;

    public static void save(Context context, String userMessage, String assistantMessage) {
        String user = shorten(userMessage, 500);
        List<String> chunks = makeChunks(context, assistantMessage);
        if (chunks.isEmpty()) return;

        long turnId = System.currentTimeMillis();
        List<MemoryChunk> memories = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            float[] vector = BgeEmbedder.embed(
                    context, shorten(user, 200) + "\n" + chunk);
            if (vector == null) continue;

            MemoryChunk memory = new MemoryChunk();
            memory.turnId = turnId;
            memory.chunkIndex = i;
            memory.userMessage = user;
            memory.assistantMessage = chunk;
            memory.createdAt = turnId;
            memory.vector = NoticeVector.toBytes(vector);
            memories.add(memory);
        }
        if (memories.isEmpty()) return;
        AppDatabase.get(context).memoryChunkDao().insertAll(memories);
        AppDatabase.get(context).memoryChunkDao().keepLatestTurns(MAX_TURN_COUNT);
    }

    public static String findRelevant(Context context, String question, int count, int recentToSkip) {
        try {
            List<MemoryChunk> all = AppDatabase.get(context).memoryChunkDao().getAllNewestFirst();
            if (all.size() <= recentToSkip) return "";

            float[] queryVector = BgeEmbedder.embed(context, question);
            if (queryVector == null) return "";

            Set<Long> skippedTurns = new HashSet<>();
            for (MemoryChunk memory : all) {
                if (skippedTurns.size() >= recentToSkip) break;
                skippedTurns.add(memory.turnId);
            }

            List<ScoredMemory> scored = new ArrayList<>();
            for (MemoryChunk memory : all) {
                if (skippedTurns.contains(memory.turnId)) continue;
                float[] vector = NoticeVector.toFloats(memory.vector);
                if (vector == null || vector.length != queryVector.length) continue;
                float score = dot(queryVector, vector);
                if (score >= SCORE_THRESHOLD) scored.add(new ScoredMemory(memory, score));
            }
            scored.sort(Comparator.comparingDouble((ScoredMemory item) -> item.score).reversed());

            StringBuilder result = new StringBuilder();
            for (int i = 0; i < Math.min(count, scored.size()); i++) {
                MemoryChunk memory = scored.get(i).memory;
                result.append("用户：").append(shorten(memory.userMessage, 300)).append("\n")
                        .append("助手片段：").append(memory.assistantMessage).append("\n\n");
            }
            return result.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static float dot(float[] a, float[] b) {
        float result = 0;
        for (int i = 0; i < a.length; i++) result += a[i] * b[i];
        return result;
    }

    private static List<String> makeChunks(Context context, String text) {
        List<String> sentences = BgeEmbedder.splitSentences(context, text);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sentence : sentences) {
            if (sentence == null || sentence.trim().isEmpty()) continue;
            String clean = sentence.trim();
            while (clean.length() > CHUNK_LENGTH) {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current.setLength(0);
                    if (chunks.size() >= MAX_CHUNKS_PER_TURN) return chunks;
                }
                chunks.add(clean.substring(0, CHUNK_LENGTH));
                clean = clean.substring(CHUNK_LENGTH);
                if (chunks.size() >= MAX_CHUNKS_PER_TURN) return chunks;
            }
            if (current.length() > 0 && current.length() + clean.length() > CHUNK_LENGTH) {
                chunks.add(current.toString());
                current.setLength(0);
                if (chunks.size() >= MAX_CHUNKS_PER_TURN) return chunks;
            }
            current.append(clean);
        }
        if (current.length() > 0 && chunks.size() < MAX_CHUNKS_PER_TURN) {
            chunks.add(current.toString());
        }
        if (chunks.isEmpty() && text != null && !text.trim().isEmpty()) {
            chunks.add(shorten(text, CHUNK_LENGTH));
        }
        return chunks;
    }

    private static String shorten(String text, int maxLength) {
        if (text == null) return "";
        String clean = text.trim();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength) + "…";
    }

    private static class ScoredMemory {
        final MemoryChunk memory;
        final float score;

        ScoredMemory(MemoryChunk memory, float score) {
            this.memory = memory;
            this.score = score;
        }
    }
}
