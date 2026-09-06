package com.example.seu_agent;

/** Java 调用 native-lib.cpp 的入口。 */
public class NativeBridge {

    static {
        System.loadLibrary("seu_agent");
    }

    // 普通接口响应
    public native boolean isApiOk(String responseJson);

    // 把本地检索结果排成提示词
    // 大模型请求
    public native String buildLlmRequest(String userPrompt, String context, String model, String toolsJson);

    // 工具执行完以后构造续轮请求
    public native String buildLlmToolRequest(String context, String model, String toolsJson,
                                             String traceJson, String callsJson,
                                             String resultsJson, String reasoning);

    public native String parseLlmReply(String responseJson);

    // 分别取 SSE 一行里的回答、工具调用和思考内容
    public native String parseLlmStreamLine(String line);

    public native String parseLlmStreamToolCall(String line);

    public native String parseLlmStreamReasoning(String line);

    // BGE
    public native boolean initBge(String modelPath, String vocabPath);

    public native float[] embedText(String text);

    public native String[] splitSentences(String text);

    // 聊天历史暂存在 C++ 内存里
    public native void addChatMessage(String text, boolean isUser);

    public native void clearChatHistory();
}
