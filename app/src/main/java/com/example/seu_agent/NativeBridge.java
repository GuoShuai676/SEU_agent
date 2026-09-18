package com.example.seu_agent;

public class NativeBridge {

    static {
        System.loadLibrary("seu_agent");
    }

    public native boolean isApiOk(String responseJson);

    public native String buildLlmRequest(String userPrompt, String context, String model, String toolsJson,
                                         String systemPrompt);

    public native String buildLlmToolRequest(String context, String model, String toolsJson,
                                             String traceJson, String callsJson,
                                             String resultsJson, String reasoning, String systemPrompt);

    public native String parseLlmReply(String responseJson);

    public native String parseLlmStreamLine(String line);

    public native String parseLlmStreamToolCall(String line);

    public native String parseLlmStreamReasoning(String line);

    public native boolean initBge(String modelPath, String vocabPath);

    public native float[] embedText(String text);

    public native String[] splitSentences(String text);

    public native void addChatMessage(String text, boolean isUser);

    public native void clearChatHistory();
}
