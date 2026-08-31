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
     * @return {"model":..,"messages":[system(含上下文), 历史..., user], "stream":true}
     */
    public native String buildLlmRequest(String userPrompt, String context, String model);

    /** 解析 DeepSeek 非流式响应，提取 choices[0].message.content */
    public native String parseLlmReply(String responseJson);

    /**
     * 解析 DeepSeek SSE 流式的一行，返回增量文本。
     * 传入形如 "data: {...}" 的一行；非 data 行 / [DONE] / 无内容返回空串。
     */
    public native String parseLlmStreamLine(String line);

    // ===== 聊天历史（多轮对话，C++ 内存维护） =====

    /** 添加聊天消息到历史（isUser=true 为用户，false 为助手），保留最近 20 条 */
    public native void addChatMessage(String text, boolean isUser);

    /** 清空聊天历史 */
    public native void clearChatHistory();
}
