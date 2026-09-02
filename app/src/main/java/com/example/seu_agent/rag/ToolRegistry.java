package com.example.seu_agent.rag;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具注册表：集中管理所有 AgentTool，负责
 * ① 生成 tools 声明 JSON（拼进 DeepSeek 请求，让模型知道有哪些工具）；
 * ② 按工具名派发执行。
 *
 * 扩展方式：registry.register(new MyTool()) 一行注册即可，其余流程不用动。
 */
public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    /** 注册工具（同名覆盖） */
    public void register(AgentTool tool) {
        tools.put(tool.getName(), tool);
    }

    public AgentTool get(String name) {
        return tools.get(name);
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }

    /** 生成 OpenAI 风格 tools 数组 JSON；没有任何工具或组装失败时返回空串（请求不带 tools） */
    public String buildToolsJson() {
        if (tools.isEmpty()) return "";
        try {
            JSONArray arr = new JSONArray();
            for (AgentTool t : tools.values()) {
                JSONObject fn = new JSONObject();
                fn.put("name", t.getName());
                fn.put("description", t.getDescription());
                fn.put("parameters", new JSONObject(t.getParametersJson()));
                JSONObject tool = new JSONObject();
                tool.put("type", "function");
                tool.put("function", fn);
                arr.put(tool);
            }
            return arr.toString();
        } catch (Exception e) {
            return ""; // 声明失败则本次请求不启用工具
        }
    }

    /** 执行工具；工具不存在或执行出错时返回错误文本（模型会看到并自行处理） */
    public String execute(String name, String argumentsJson) {
        AgentTool t = tools.get(name);
        if (t == null) return "未知工具：" + name + "，请勿调用不存在的工具";
        try {
            return t.execute(argumentsJson == null ? "{}" : argumentsJson);
        } catch (Exception e) {
            return "工具执行出错：" + e.getMessage();
        }
    }

    public Collection<AgentTool> all() {
        return tools.values();
    }
}
