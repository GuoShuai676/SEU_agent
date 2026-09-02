package com.example.seu_agent.rag;

/**
 * 智能体工具接口：暴露给大模型的"可调用能力"。
 *
 * 扩展方式（这就是你留的接口）：
 *   1. 新建类实现本接口（getName / getDescription / getParametersJson / execute）；
 *   2. 在 ToolRegistry 里 register 一行，模型就能在对话中自主调用它。
 *
 * 示例工具见 SearchNoticesTool。
 */
public interface AgentTool {

    /** 工具名（模型调用时填的名字，如 search_notices），必须唯一 */
    String getName();

    /** 工具描述：告诉模型"什么时候该用、能查到什么"，越具体模型判断越准 */
    String getDescription();

    /**
     * 参数 JSON Schema（OpenAI function calling 格式）：
     * {"type":"object","properties":{"xxx":{"type":"string","description":"..."}},"required":["xxx"]}
     */
    String getParametersJson();

    /**
     * 执行工具。
     *
     * @param argumentsJson 模型填的参数 JSON，如 {"keyword":"选课"}
     * @return 给模型看的执行结果文本（模型会基于它生成最终回答）
     */
    String execute(String argumentsJson) throws Exception;
}
