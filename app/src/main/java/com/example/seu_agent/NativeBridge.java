package com.example.seu_agent;

/**
 * JNI 桥接层：Java 与 C++ 逻辑的接口（实现见 cpp/native-lib.cpp）。
 * 用途详见开发文档「JNI 接口」章节。
 *
 * 分工：
 *   C++ = 请求组装 / JSON 解析 / 聊天历史 / SSE 流式解析
 *   Java = UI + 网络（手机直连 DeepSeek 时，请求体由 C++ 生成）
 */
public class NativeBridge {

    static {
        System.loadLibrary("seu_agent");
    }

    // ===== 服务器响应 =====

    /** 检查服务器响应是否成功（code==0 或 ok==true），用于 /api/notices 等 */
    public native boolean isApiOk(String responseJson);

    // ===== 本地检索 → 上下文 =====

    /**
     * 从本地检索结果构建大模型上下文文本。
     * @param itemsJson 本地检索结果 JSON：[{"title":..,"date":..,"content":..},...]
     * @return 格式化上下文 "【date】title\ncontent..."（最多 5 条，正文截断 800 字）
     */
    public native String buildContext(String itemsJson);

    // ===== 大模型请求 / 响应（手机直连 DeepSeek） =====

    /**
     * 构建 DeepSeek 聊天请求 JSON。
     * @param userPrompt 当前用户提问
     * @param context    资讯上下文（buildContext 的返回值）
     * @param model      模型名（deepseek-chat / deepseek-reasoner）
     * @param toolsJson  工具声明（ToolRegistry.buildToolsJson()），空串则不声明
     * @return {"model":..,"messages":[system(含上下文), 历史...], "stream":true, "tools":[..]}
     */
    public native String buildLlmRequest(String userPrompt, String context, String model, String toolsJson);

    /**
     * 工具调用续轮请求：在消息里追加 assistant(tool_calls 数组) + tool(结果数组)，
     * 让模型基于工具结果生成最终回答。thinking 模型会带上 reasoning_content 回传。
     * @param callsJson     [{"id":"call_x","name":"search_notices","arguments":"{...}"},...]
     * @param resultsJson   [{"id":"call_x","content":"结果文本"},...]
     * @param reasoning     上轮思考内容（thinking 模型必填，普通模型传空串）
     */
    public native String buildLlmToolRequest(String context, String model, String toolsJson,
                                             String callsJson, String resultsJson, String reasoning);

    /** 解析 DeepSeek 非流式响应，提取 choices[0].message.content */
    public native String parseLlmReply(String responseJson);

    /**
     * 解析 DeepSeek SSE 流式的一行，返回增量文本。
     * 传入形如 "data: {...}" 的一行；非 data 行 / [DONE] / 无内容返回空串。
     */
    public native String parseLlmStreamLine(String line);

    /**
     * 解析 SSE 流式一行中的工具调用增量，返回 {"index":N,"id":..,"name":..,"arguments":..}。
     * arguments 可能是分片（模型边生成边下发），需要调用方拼接；该行无工具调用时返回空串。
     */
    public native String parseLlmStreamToolCall(String line);

    /** 解析 SSE 流式一行中的思考增量（thinking 模型），无则返回空串 */
    public native String parseLlmStreamReasoning(String line);

    // ===== 本地语义嵌入（C++ 实现：分词 + ONNX 推理） =====

    /** 加载 bge 模型与词表（modelPath/vocabPath 为文件路径），成功返回 true */
    public native boolean initBge(String modelPath, String vocabPath);

    /** 文本 → 512 维归一化向量（需先 initBge） */
    public native float[] embedText(String text);

    /** 中文按句切分（。！？；… 换行）→ 句子数组 */
    public native String[] splitSentences(String text);

    // ===== 聊天历史（多轮对话，C++ 内存维护） =====

    /** 添加聊天消息到历史（isUser=true 为用户，false 为助手），保留最近 20 条 */
    public native void addChatMessage(String text, boolean isUser);

    /** 清空聊天历史 */
    public native void clearChatHistory();
}
