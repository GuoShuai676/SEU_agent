package com.example.seu_agent;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 应用配置（SharedPreferences 存储）：
 * 服务器 URL / 模型 / 模型 URL / API Key。
 * 聊天请求时从这读取，带上 llm_url / api_key / model。
 */
public class AppConfig {

    private static final String PREFS = "app_config";

    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_LLM_MODEL = "llm_model";
    private static final String KEY_LLM_URL = "llm_url";
    private static final String KEY_API_KEY = "api_key";

    public static final String DEFAULT_SERVER_URL = "http://103.236.89.13:8003";
    public static final String DEFAULT_LLM_URL = "https://api.deepseek.com/v1/chat/completions";
    public static final String DEFAULT_MODEL = "deepseek-chat";

    public static String getServerUrl(Context c) {
        return prefs(c).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL);
    }

    public static String getLlmModel(Context c) {
        return prefs(c).getString(KEY_LLM_MODEL, DEFAULT_MODEL);
    }

    public static String getLlmUrl(Context c) {
        return prefs(c).getString(KEY_LLM_URL, DEFAULT_LLM_URL);
    }

    public static String getApiKey(Context c) {
        return prefs(c).getString(KEY_API_KEY, "");
    }

    public static void save(Context c, String serverUrl, String model, String llmUrl, String apiKey) {
        prefs(c).edit()
                .putString(KEY_SERVER_URL, serverUrl == null ? "" : serverUrl.trim())
                .putString(KEY_LLM_MODEL, model == null ? DEFAULT_MODEL : model.trim())
                .putString(KEY_LLM_URL, llmUrl == null ? "" : llmUrl.trim())
                .putString(KEY_API_KEY, apiKey == null ? "" : apiKey.trim())
                .apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
