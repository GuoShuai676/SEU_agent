package com.example.seu_agent.rag;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolRegistry {

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public void register(AgentTool tool) {
        tools.put(tool.getName(), tool);
    }

    public AgentTool get(String name) {
        return tools.get(name);
    }

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
            return "";
        }
    }

    public String execute(String name, String argumentsJson) {
        AgentTool t = tools.get(name);
        if (t == null) return "未知工具：" + name + "，请勿调用不存在的工具";
        try {
            return t.execute(argumentsJson == null ? "{}" : argumentsJson);
        } catch (Exception e) {
            return "工具执行出错：" + e.getMessage();
        }
    }
}
