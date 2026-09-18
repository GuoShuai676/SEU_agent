package com.example.seu_agent.rag;

public interface AgentTool {

    String getName();

    String getDescription();

    String getParametersJson();

    String execute(String argumentsJson) throws Exception;
}
