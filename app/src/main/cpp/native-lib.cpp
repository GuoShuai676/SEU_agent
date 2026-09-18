#include <jni.h>
#include <string>
#include <sstream>
#include <vector>
#include <utility>
#include <atomic>
#include <android/log.h>

#include "json_parser.h"

#define LOG_TAG "campus_native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::string Json_Convert(const std::string s)
{
    std::string out;
    out.reserve(s.size()+16);
    static const char* hex = "0123456789abcdef";
    for(char c: s)
    {
        switch(c)
        {
            case '"': out +="\\\"";
                break;
            case '\\': out +="\\\\" ;
                break;
            case '\r': out+="\\r";
                break;
            case '\n': out+="\\n";
                break;
            case '\t': out+="\\t";
                break;
            case '\b': out+="\\b";
                break;
            case '\f': out+="\\f";
                break;
            default:
                unsigned char uc = (unsigned char)c;
                if (uc < 0x20 || uc == 0x7f) {
                    out += "\\u00";
                    out += hex[uc >> 4];
                    out += hex[uc & 0x0f];
                } else {
                    out += c;
                }
            }
    }
        return out;
}

static std::string URL_Generate(std::string s)
{   static const char* hex="0123456789ABCDEF";
    std::string out;
    out.reserve(s.size()*3);
    for(char c :s)
    {
        if((c>='a'&&c<='z')||(c>='A'&&c<='Z')||c=='~'||c=='-'||c=='.'||(c>='0'&&c<='9')||c=='_')
            out+=(char)c;
        else{
            out+='%';
            out+=hex[c>>4];
            out+=hex[c&0x0f];
        }
    }
        return out;
}

static std::string jstr(JNIEnv* env, jstring j) {
    if (!j) return "";
    const char* c = env->GetStringUTFChars(j, nullptr);
    std::string s(c ? c : "");
    env->ReleaseStringUTFChars(j, c);
    return s;
}

static std::string SanitizeUTF8(const std::string& s)
{
    std::string out;
    out.reserve(s.size());
    size_t i = 0, n = s.size();
    while (i < n) {
        unsigned char c = (unsigned char)s[i];
        if (c < 0x80) { out += (char)c; i++; continue; }          // 单字节 ASCII
        int len = 0;
        if      ((c & 0xE0) == 0xC0) len = 2;                     // 2字节
        else if ((c & 0xF0) == 0xE0) len = 3;                     // 3字节（中文）
        else if ((c & 0xF8) == 0xF0) len = 4;                     // 4字节（Emoji）
        else { out += '?'; i++; continue; }                       // 非法起始字节
        if (i + len > n) break;                                   // 结尾不完整：丢弃
        bool ok = true;
        for (int k = 1; k < len; k++) {
            if (((unsigned char)s[i + k] & 0xC0) != 0x80) { ok = false; break; }
        }
        if (ok) { out.append(s, i, len); i += len; }
        else    { out += '?'; i++; }
    }
    return out;
}
static std::vector<std::pair<std::string, std::string>> g_chatHistory;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_seu_1agent_NativeBridge_isApiOk(JNIEnv* env, jobject, jstring jResp) {
    JsonValue root = JsonValue::parse(jstr(env, jResp));
    const JsonValue* code = root.find("code");
    if (code) return code->asInt() == 0 ? JNI_TRUE : JNI_FALSE;
    const JsonValue* ok = root.find("ok");
    if (ok) return ok->asBool() ? JNI_TRUE : JNI_FALSE;
    return JNI_FALSE;
}


static std::string BuildSystemPrompt(const std::string& prompt, const std::string& context) {
    if (!context.empty()) {
        return prompt + "\n\n下面是通过语义检索找到的历史对话，"
               "仅用于理解用户的上下文和以前讨论过的内容，不代表学校官方事实；"
               "涉及实时信息或校园通知时仍应调用对应工具核实。\n\n"
               "=== 相关历史记忆 ===\n" + context + "\n=== 历史记忆结束 ===";
    }
    return prompt;
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_example_seu_1agent_NativeBridge_buildLlmRequest(
        JNIEnv* env, jobject, jstring jPrompt, jstring jContext, jstring jModel, jstring jTools,
        jstring jSystemPrompt) {
    std::string prompt = jstr(env, jPrompt);
    std::string context = jstr(env, jContext);
    std::string model = jstr(env, jModel);
    std::string toolsJson = jstr(env, jTools);
    if (model.empty()) model = "deepseek-v4-flash";

    std::string sys = BuildSystemPrompt(jstr(env, jSystemPrompt), context);

    std::ostringstream ss;
    ss << "{\"model\":\"" << model << "\",\"messages\":[";
    ss << "{\"role\":\"system\",\"content\":\"" << Json_Convert(sys) << "\"}";
    for (const auto& [role, content] : g_chatHistory) {
        ss << ",{\"role\":\"" << role << "\",\"content\":\"" << Json_Convert(content) << "\"}";
    }
    ss << "],\"stream\":true";
    if (!toolsJson.empty()) {
        ss << ",\"tools\":" << toolsJson << ",\"tool_choice\":\"auto\"";
    }
    ss << "}";
    return env->NewStringUTF(SanitizeUTF8(ss.str()).c_str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_example_seu_1agent_NativeBridge_buildLlmToolRequest(
        JNIEnv* env, jobject, jstring jContext, jstring jModel, jstring jTools,
        jstring jTrace, jstring jCalls, jstring jResults, jstring jReasoning,
        jstring jSystemPrompt) {
    std::string context = jstr(env, jContext);
    std::string model = jstr(env, jModel);
    std::string toolsJson = jstr(env, jTools);
    JsonValue trace = JsonValue::parse(jstr(env, jTrace));     // 之前完成的工具交互
    JsonValue calls = JsonValue::parse(jstr(env, jCalls));     // [{"id":..,"name":..,"arguments":..},...]
    JsonValue results = JsonValue::parse(jstr(env, jResults)); // [{"id":..,"content":..},...]
    std::string reasoning = jstr(env, jReasoning);
    if (model.empty()) model = "deepseek-v4-flash";

    static std::atomic<int> g_callSeq{0};

    std::string sys = BuildSystemPrompt(jstr(env, jSystemPrompt), context);
    std::ostringstream ss;
    ss << "{\"model\":\"" << model << "\",\"messages\":[";
    ss << "{\"role\":\"system\",\"content\":\"" << Json_Convert(sys) << "\"}";
    for (const auto& [role, content] : g_chatHistory) {
        ss << ",{\"role\":\"" << role << "\",\"content\":\"" << Json_Convert(content) << "\"}";
    }
    auto appendExchange = [&](const JsonValue& exchangeCalls, const JsonValue& exchangeResults,
                              const std::string& exchangeReasoning) {
        ss << ",{\"role\":\"assistant\"";
        if (!exchangeReasoning.empty()) {
            ss << ",\"reasoning_content\":\"" << Json_Convert(exchangeReasoning) << "\"";
        }
        ss << ",\"content\":null,\"tool_calls\":[";
        for (size_t i = 0; i < exchangeCalls.size(); i++) {
            const JsonValue* c = exchangeCalls.at(i);
            const JsonValue* id = c ? c->find("id") : nullptr;
            const JsonValue* name = c ? c->find("name") : nullptr;
            const JsonValue* args = c ? c->find("arguments") : nullptr;
            std::string idS = (id && !id->asString().empty())
                    ? id->asString() : ("call_" + std::to_string(++g_callSeq));
            if (i > 0) ss << ",";
            ss << "{\"id\":\"" << Json_Convert(idS)
               << "\",\"type\":\"function\",\"function\":{\"name\":\""
               << Json_Convert(name ? name->asString() : "") << "\",\"arguments\":\""
               << Json_Convert(args ? args->asString() : "{}") << "\"}}";
        }
        ss << "]}";
        for (size_t i = 0; i < exchangeResults.size(); i++) {
            const JsonValue* r = exchangeResults.at(i);
            const JsonValue* rid = r ? r->find("id") : nullptr;
            const JsonValue* content = r ? r->find("content") : nullptr;
            ss << ",{\"role\":\"tool\",\"tool_call_id\":\""
               << Json_Convert(rid ? rid->asString() : "") << "\",\"content\":\""
               << Json_Convert(content ? content->asString() : "") << "\"}";
        }
    };

    for (size_t i = 0; i < trace.size(); i++) {
        const JsonValue* item = trace.at(i);
        const JsonValue* oldCalls = item ? item->find("calls") : nullptr;
        const JsonValue* oldResults = item ? item->find("results") : nullptr;
        const JsonValue* oldReasoning = item ? item->find("reasoning") : nullptr;
        if (oldCalls && oldResults) {
            appendExchange(*oldCalls, *oldResults,
                           oldReasoning ? oldReasoning->asString() : "");
        }
    }
    appendExchange(calls, results, reasoning);
    ss << "],\"stream\":true";
    if (!toolsJson.empty()) {
        ss << ",\"tools\":" << toolsJson << ",\"tool_choice\":\"auto\"";
    }
    ss << "}";
    return env->NewStringUTF(SanitizeUTF8(ss.str()).c_str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_example_seu_1agent_NativeBridge_parseLlmReply(JNIEnv* env, jobject, jstring jResp) {
    JsonValue root = JsonValue::parse(jstr(env, jResp));
    const JsonValue* choices = root.find("choices");
    if (!choices || choices->size() == 0) return env->NewStringUTF("[无回复]");
    const JsonValue* msg = choices->at(0)->find("message");
    const JsonValue* content = msg ? msg->find("content") : nullptr;
    return env->NewStringUTF(SanitizeUTF8(content ? content->asString() : "[空回复]").c_str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_example_seu_1agent_NativeBridge_parseLlmStreamLine(JNIEnv* env, jobject, jstring jLine) {
    std::string line = jstr(env, jLine);
    if (line.rfind("data:", 0) != 0) return env->NewStringUTF("");
    std::string payload = line.substr(5);
    size_t s = payload.find_first_not_of(" \t");
    if (s != std::string::npos) payload = payload.substr(s);
    if (payload == "[DONE]") return env->NewStringUTF("");

    JsonValue v = JsonValue::parse(payload);
    const JsonValue* choices = v.find("choices");
    if (!choices || choices->size() == 0) return env->NewStringUTF("");
    const JsonValue* delta = choices->at(0)->find("delta");
    const JsonValue* content = delta ? delta->find("content") : nullptr;
    return env->NewStringUTF(SanitizeUTF8(content ? content->asString() : "").c_str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_example_seu_1agent_NativeBridge_parseLlmStreamToolCall(JNIEnv* env, jobject, jstring jLine) {
    std::string line = jstr(env, jLine);
    if (line.rfind("data:", 0) != 0) return env->NewStringUTF("");
    std::string payload = line.substr(5);
    size_t s = payload.find_first_not_of(" \t");
    if (s != std::string::npos) payload = payload.substr(s);
    if (payload == "[DONE]") return env->NewStringUTF("");

    JsonValue v = JsonValue::parse(payload);
    const JsonValue* choices = v.find("choices");
    if (!choices || choices->size() == 0) return env->NewStringUTF("");
    const JsonValue* delta = choices->at(0)->find("delta");
    const JsonValue* tcs = delta ? delta->find("tool_calls") : nullptr;
    if (!tcs || tcs->size() == 0) return env->NewStringUTF("");
    const JsonValue* tc = tcs->at(0);
    if (!tc) return env->NewStringUTF("");

    std::string out = "{";
    bool first = true;
    auto addField = [&](const std::string& key, const std::string& val) {
        if (val.empty()) return;
        if (!first) out += ",";
        first = false;
        out += "\"" + key + "\":\"" + Json_Convert(val) + "\"";
    };
    const JsonValue* idx = tc->find("index");   // 并行多个工具调用时用 index 区分
    if (idx) addField("index", std::to_string((long long) idx->asInt()));
    const JsonValue* id = tc->find("id");
    if (id) addField("id", id->asString());
    const JsonValue* fn = tc->find("function");
    if (fn) {
        const JsonValue* name = fn->find("name");
        if (name) addField("name", name->asString());
        const JsonValue* args = fn->find("arguments");
        if (args) addField("arguments", args->asString());
    }
    out += "}";
    return env->NewStringUTF(SanitizeUTF8(out).c_str());
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_example_seu_1agent_NativeBridge_parseLlmStreamReasoning(JNIEnv* env, jobject, jstring jLine) {
    std::string line = jstr(env, jLine);
    if (line.rfind("data:", 0) != 0) return env->NewStringUTF("");
    std::string payload = line.substr(5);
    size_t s = payload.find_first_not_of(" \t");
    if (s != std::string::npos) payload = payload.substr(s);
    if (payload == "[DONE]") return env->NewStringUTF("");

    JsonValue v = JsonValue::parse(payload);
    const JsonValue* choices = v.find("choices");
    if (!choices || choices->size() == 0) return env->NewStringUTF("");
    const JsonValue* delta = choices->at(0)->find("delta");
    const JsonValue* rc = delta ? delta->find("reasoning_content") : nullptr;
    return env->NewStringUTF(SanitizeUTF8(rc ? rc->asString() : "").c_str());
}


extern "C" JNIEXPORT void JNICALL
Java_com_example_seu_1agent_NativeBridge_addChatMessage(
        JNIEnv* env, jobject, jstring jText, jboolean isUser) {
    std::string text = jstr(env, jText);
    g_chatHistory.push_back({isUser ? "user" : "assistant", text});
    size_t limit = isUser ? 13 : 12;
    while (g_chatHistory.size() > limit) g_chatHistory.erase(g_chatHistory.begin());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_seu_1agent_NativeBridge_clearChatHistory(JNIEnv*, jobject) {
    g_chatHistory.clear();
}
