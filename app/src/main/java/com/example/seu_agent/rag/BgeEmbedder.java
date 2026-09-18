package com.example.seu_agent.rag;

import android.content.Context;

import com.example.seu_agent.NativeBridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class BgeEmbedder {

    public static final int DIM = 512;

    private static final String MODEL_ASSET = "bge-small-zh-int8.onnx";
    private static final String VOCAB_ASSET = "vocab.txt";

    private static volatile boolean inited = false;
    private static final NativeBridge BRIDGE = new NativeBridge();

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

    public static float[] embed(Context ctx, String text) {
        if (!ensureInit(ctx)) return null;
        synchronized (BRIDGE) {
            return BRIDGE.embedText(text);
        }
    }

    public static List<String> splitSentences(Context ctx, String content) {
        if (!ensureInit(ctx)) return java.util.Collections.emptyList();
        String[] arr = BRIDGE.splitSentences(content == null ? "" : content);
        return arr == null ? java.util.Collections.emptyList() : Arrays.asList(arr);
    }

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
