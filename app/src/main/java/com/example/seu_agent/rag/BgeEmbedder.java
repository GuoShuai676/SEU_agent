package com.example.seu_agent.rag;

import android.content.Context;

import com.example.seu_agent.NativeBridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * 语义嵌入的 Java 壳：真实实现（分词 + ONNX 推理）在 C++（cpp/bge.cpp），经 JNI 调用。
 * 职责：把 assets 里的模型/词表复制到 cache，再调用 native initBge / embedText / splitSentences。
 * 单例：模型只初始化一次，session 可并发推理。
 */
public class BgeEmbedder {

    public static final int DIM = 512;

    private static final String MODEL_ASSET = "bge-small-zh-int8.onnx";
    private static final String VOCAB_ASSET = "vocab.txt";

    private static volatile boolean inited = false;
    private static final NativeBridge BRIDGE = new NativeBridge();

    /** 初始化（后台线程调用；幂等）。失败返回 false */
    public static boolean ensureInit(Context ctx) {
        if (inited) return true;
        synchronized (BgeEmbedder.class) {
            if (inited) return true;
            try {
                File model = copyAssetToCache(ctx, MODEL_ASSET);
                File vocab = copyAssetToCache(ctx, VOCAB_ASSET);
                inited = BRIDGE.initBge(model.getAbsolutePath(), vocab.getAbsolutePath());
            } catch (Exception e) {
                return false;
            }
            return inited;
        }
    }

    public static boolean isReady() {
        return inited;
    }

    /** 文本 → 512 维归一化向量；未初始化/失败返回 null */
    public static float[] embed(Context ctx, String text) {
        if (!ensureInit(ctx)) return null;
        return BRIDGE.embedText(text);
    }

    /** 中文按句切分（。！？；… 换行）；未初始化返回空列表 */
    public static List<String> splitSentences(Context ctx, String content) {
        if (!ensureInit(ctx)) return java.util.Collections.emptyList();
        String[] arr = BRIDGE.splitSentences(content == null ? "" : content);
        return arr == null ? java.util.Collections.emptyList() : Arrays.asList(arr);
    }

    /** 模型文件复制到 cache 目录（ONNX Runtime 需要文件路径） */
    private static File copyAssetToCache(Context ctx, String name) throws Exception {
        File f = new File(ctx.getCacheDir(), name);
        try (InputStream in = ctx.getAssets().open(name);
             FileOutputStream out = new FileOutputStream(f)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return f;
    }
}
