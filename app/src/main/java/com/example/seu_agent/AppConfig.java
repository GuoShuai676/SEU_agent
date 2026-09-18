package com.example.seu_agent;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppConfig {

    private static final String PREFS = "app_config";
    private static final String KEY_LLM_MODEL = "llm_model";
    private static final String KEY_LLM_URL = "llm_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_SYSTEM_PROMPT = "system_prompt";
    private static final String KEY_MODELS = "models";
    private static final String KEY_NAME = "profile_name";
    private static final String KEY_STUDENT_ID = "profile_student_id";
    private static final String KEY_MAJOR = "profile_major";
    private static final String KEY_GRADE = "profile_grade";
    private static final String KEY_HOBBIES = "profile_hobbies";
    private static final String KEY_FIRST_MONDAY = "semester_first_monday";

    public static final String DEFAULT_SERVER_URL = "http://103.236.92.58:8003";
    public static final String DEFAULT_LLM_URL = "https://api.deepseek.com/v1/chat/completions";
    public static final String DEFAULT_MODEL = "deepseek-v4-flash";
    public static final String DEFAULT_SYSTEM_PROMPT =
            "你是东南大学校园智能助手，回答校园相关问题。如果用户的问题涉及具体的"
            + "教务通知、讲座或实践信息，请调用对应工具核实，不要凭空编造细节。\n\n"
            + "你可以使用工具完成任务。收到问题后先在内部制定一个简短计划，不要向用户展示思维链。"
            + "需要事实或外部信息时调用工具；互不依赖的工具可以在同一轮一起调用。"
            + "每次得到工具结果后，把它当作观察结果重新检查并动态调整计划。"
            + "信息不足时可以继续调用其他工具；信息足够后停止调用并给出直接、完整的最终回答。"
            + "不得编造工具没有返回的事实，也不要重复调用参数完全相同的工具。"
            + "使用校园资讯检索结果回答时，必须在答案末尾用Markdown格式"
            + "[通知标题](原文链接)列出所依据的原文，绝不能编造或修改链接。";

    public static String getSystemPrompt(Context c) {
        String value = prefs(c).getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT);
        return value == null || value.trim().isEmpty() ? DEFAULT_SYSTEM_PROMPT : value;
    }

    public static boolean saveSystemPrompt(Context c, String prompt) {
        return prefs(c).edit().putString(KEY_SYSTEM_PROMPT, prompt.trim()).commit();
    }

    public static boolean resetSystemPrompt(Context c) {
        return prefs(c).edit().remove(KEY_SYSTEM_PROMPT).commit();
    }


    private static final List<String> DEFAULT_MODELS =
            Arrays.asList( "deepseek-v4-flash", "deepseek-v4-pro");


    public static String getServerUrl(Context c) {
        return DEFAULT_SERVER_URL;
    }

    public static String getLlmModel(Context c) {
        return prefs(c).getString(KEY_LLM_MODEL, DEFAULT_MODEL);
    }


    public static void setModel(Context c, String model) {
        if (model == null || model.trim().isEmpty()) return;
        prefs(c).edit().putString(KEY_LLM_MODEL, model.trim()).apply();
    }

    public static String getLlmUrl(Context c) {
        return prefs(c).getString(KEY_LLM_URL, DEFAULT_LLM_URL);
    }

    public static String getApiKey(Context c) {
        return prefs(c).getString(KEY_API_KEY, "");
    }

    public static String getProfileName(Context c) {
        return prefs(c).getString(KEY_NAME, "未填写");
    }

    public static String getStudentId(Context c) {
        return prefs(c).getString(KEY_STUDENT_ID, "未填写");
    }

    public static String getMajor(Context c) {
        return prefs(c).getString(KEY_MAJOR, "未填写");
    }

    public static String getGrade(Context c) {
        return prefs(c).getString(KEY_GRADE, "未填写");
    }

    public static String getHobbies(Context c) {
        return prefs(c).getString(KEY_HOBBIES, "未填写");
    }

    public static long getFirstMonday(Context c) {
        return prefs(c).getLong(KEY_FIRST_MONDAY, 0L);
    }

    public static void setFirstMonday(Context c, long millis) {
        prefs(c).edit().putLong(KEY_FIRST_MONDAY, millis).apply();
    }

    public static void saveProfile(Context c, String name, String studentId, String major,
                                   String grade, String hobbies) {
        prefs(c).edit()
                .putString(KEY_NAME, valueOrEmpty(name))
                .putString(KEY_STUDENT_ID, valueOrEmpty(studentId))
                .putString(KEY_MAJOR, valueOrEmpty(major))
                .putString(KEY_GRADE, valueOrEmpty(grade))
                .putString(KEY_HOBBIES, valueOrEmpty(hobbies))
                .apply();
    }

    private static String valueOrEmpty(String value) {
        if (value == null || value.trim().isEmpty()) return "未填写";
        return value.trim();
    }


    public static List<String> getModels(Context c) {
        String raw = prefs(c).getString(KEY_MODELS, "");
        if (raw == null || raw.isEmpty()) return new ArrayList<>(DEFAULT_MODELS);
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i));
        } catch (Exception e) {
            return new ArrayList<>(DEFAULT_MODELS);
        }
        return out;
    }


    public static void addModel(Context c, String model) {
        if (model == null || model.trim().isEmpty()) return;
        String m = model.trim();
        List<String> list = getModels(c);
        if (!list.contains(m)) list.add(m);
        saveModels(c, list);
    }

    public static void removeModel(Context c, String model) {
        if (model == null) return;
        List<String> list = getModels(c);
        list.remove(model);
        saveModels(c, list);
    }

    private static void saveModels(Context c, List<String> list) {
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        prefs(c).edit().putString(KEY_MODELS, arr.toString()).apply();
    }


    public static boolean save(Context c, String model, String llmUrl, String apiKey) {
        return prefs(c).edit()
                .putString(KEY_LLM_MODEL, model == null ? DEFAULT_MODEL : model.trim())
                .putString(KEY_LLM_URL, llmUrl == null ? "" : llmUrl.trim())
                .putString(KEY_API_KEY, apiKey == null ? "" : apiKey.trim())
                .remove("api_key_encrypted_v1")
                .commit();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
