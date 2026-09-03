package com.example.seu_agent.rag;

/** 大模型可以调用的本地工具。 */
public interface AgentTool {

    String getName();

    String getDescription();

    // function calling 使用的参数格式
    String getParametersJson();

    String execute(String argumentsJson) throws Exception;
}
