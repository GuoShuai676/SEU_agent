package com.example.seu_agent.tool;

import com.example.seu_agent.rag.AgentTool;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebSearchTool implements AgentTool {

    private static final String API_URL = "https://uapis.cn/api/v1/search/aggregate";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public String getName() {
        return "search_web";
    }

    @Override
    public String getDescription() {
        return "检索互联网上的最新公开信息，返回标题、摘要、来源、发布时间和网址。"
                + "本地校园资讯无法回答，或问题涉及最新事件、政策、新闻时调用。";
    }

    @Override
    public String getParametersJson() {
        return "{\"type\":\"object\",\"properties\":{"
                + "\"query\":{\"type\":\"string\",\"description\":\"搜索关键词\"},"
                + "\"site\":{\"type\":\"string\",\"description\":\"可选，限定网站域名，例如 seu.edu.cn\"},"
                + "\"sort\":{\"type\":\"string\",\"enum\":[\"relevance\",\"date\"],\"description\":\"相关性或时间排序\"},"
                + "\"time_range\":{\"type\":\"string\",\"enum\":[\"day\",\"week\",\"month\",\"year\"],\"description\":\"可选，限定时间范围\"}"
                + "},\"required\":[\"query\"]}";
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        JSONObject args = new JSONObject(argumentsJson);
        String query = args.optString("query", "").trim();
        if (query.isEmpty()) return "请提供搜索关键词";

        JSONObject requestJson = new JSONObject();
        requestJson.put("query", query);
        requestJson.put("fetch_full", false);
        putIfPresent(args, requestJson, "site");
        putIfPresent(args, requestJson, "sort");
        putIfPresent(args, requestJson, "time_range");

        Request request = new Request.Builder()
                .url(API_URL)
                .header("Accept", "application/json")
                .header("User-Agent", "SEU-Agent/1.0")
                .post(RequestBody.create(requestJson.toString(), JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                String message = "";
                try {
                    message = new JSONObject(body).optString("message", "");
                } catch (Exception ignored) {
                }
                return "网络检索失败（" + response.code() + "）"
                        + (message.isEmpty() ? "" : "：" + message);
            }
            return compactResult(query, new JSONObject(body));
        } catch (IOException e) {
            return "网络检索连接失败，请检查网络后重试";
        }
    }

    private String compactResult(String query, JSONObject raw) throws Exception {
        JSONObject output = new JSONObject();
        output.put("搜索词", query);
        JSONArray source = raw.optJSONArray("results");
        JSONArray results = new JSONArray();
        if (source != null) {
            for (int i = 0; i < Math.min(source.length(), 5); i++) {
                JSONObject item = source.optJSONObject(i);
                if (item == null) continue;
                JSONObject result = new JSONObject();
                copy(item, result, "title", "标题");
                copy(item, result, "snippet", "摘要");
                copy(item, result, "domain", "来源");
                copy(item, result, "publish_time", "发布时间");
                copy(item, result, "url", "网址");
                results.put(result);
            }
        }
        output.put("结果", results);
        output.put("提示", results.length() == 0
                ? "没有找到相关网页，请更换关键词"
                : "回答时应依据摘要并附上对应网址；摘要没有的信息不要推断");
        return output.toString();
    }

    private void putIfPresent(JSONObject from, JSONObject to, String name) throws Exception {
        String value = from.optString(name, "").trim();
        if (!value.isEmpty()) to.put(name, value);
    }

    private void copy(JSONObject from, JSONObject to, String oldName, String newName)
            throws Exception {
        if (from.has(oldName) && !from.isNull(oldName)) to.put(newName, from.get(oldName));
    }
}
