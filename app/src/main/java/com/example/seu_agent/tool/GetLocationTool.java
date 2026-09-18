package com.example.seu_agent.tool;

import com.example.seu_agent.rag.AgentTool;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class GetLocationTool implements AgentTool {

    private static final String API_URL = "https://uapis.cn/api/v1/network/myip";
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    public String getName() {
        return "get_current_location";
    }

    @Override
    public String getDescription() {
        return "根据当前网络的公网出口 IP 获取用户所在的大致省市。"
                + "结果不是GPS精确定位；校园网、代理或VPN可能导致地点偏差。"
                + "用户询问我在哪里、当地天气但未提供城市时调用。";
    }

    @Override
    public String getParametersJson() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public String execute(String argumentsJson) throws Exception {
        Request request = new Request.Builder()
                .url(API_URL)
                .header("Accept", "application/json")
                .header("User-Agent", "SEU-Agent/1.0")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body() == null ? "" : response.body().string();
            if (!response.isSuccessful()) {
                return "位置接口请求失败（" + response.code() + "）";
            }
            JSONObject raw = new JSONObject(body);
            JSONObject result = new JSONObject();
            copy(raw, result, "region", "大致地区");
            copy(raw, result, "province", "省份");
            copy(raw, result, "city", "城市");
            copy(raw, result, "district", "区县");
            copy(raw, result, "latitude", "纬度");
            copy(raw, result, "longitude", "经度");
            copy(raw, result, "isp", "网络运营商");
            result.put("定位方式", "公网出口IP粗略定位，非GPS");
            if (result.length() == 1) return "位置接口没有返回可用的地区信息";
            return result.toString();
        } catch (IOException e) {
            return "位置接口连接失败，请检查网络后重试";
        }
    }

    private void copy(JSONObject from, JSONObject to, String oldName, String newName)
            throws Exception {
        if (from.has(oldName) && !from.isNull(oldName)) to.put(newName, from.get(oldName));
    }
}
