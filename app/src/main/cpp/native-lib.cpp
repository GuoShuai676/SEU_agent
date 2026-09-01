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

//json字符串转义
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
                    // 其余控制字符 → \u00XX，避免请求体含非法字符被 400
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

//URL编码 把不安全的字符变成安全的，API或浏览器可用的UTF8编码，UTF8最多支持四个字节32bit
//汉字 3字节 Emoji 4字节
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

//jstring->std::string
static std::string jstr(JNIEnv* env, jstring j) {
    if (!j) return "";
    const char* c = env->GetStringUTFChars(j, nullptr);
    std::string s(c ? c : "");
    env->ReleaseStringUTFChars(j, c);
    return s;
}

//清理UTF-8：丢弃结尾不完整的字节（SSE分块可能把多字节字符切断，下一块会补上），
//把中间非法字节替换为'?'。防止 NewStringUTF 收到非法序列导致原生崩溃。
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



extern "C" JNIEXPORT jstring JNICALL
Java_com_example_seu_1agent_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}


// 聊天历史（role, content）
static std::vector<std::pair<std::string, std::string>> g_chatHistory;

//检测API
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_seu_1agent_NativeBridge_isApiOk(JNIEnv* env, jobject, jstring jResp) {
    JsonValue root = JsonValue::parse(jstr(env, jResp));
    const JsonValue* code = root.find("code");
    if (code) return code->asInt() == 0 ? JNI_TRUE : JNI_FALSE;
    const JsonValue* ok = root.find("ok");
    if (ok) return ok->asBool() ? JNI_TRUE : JNI_FALSE;
    return JNI_FALSE;
}


// ---------- 从本地检索结果构建 LLM 上下文 ----------
// itemsJson: [{"title":..,"date":..,"content":..},...]（手机本地 Room 检索结果）
// 输出："【date】title\ncontent\n\n..."（最多 5 条，正文截断 800 字）
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_seu_1agent_NativeBridge_buildContext(JNIEnv* env, jobject, jstring jItems) {
    JsonValue root = JsonValue::parse(jstr(env, jItems));
    if (!root.isArray()) return env->NewStringUTF("");
    std::ostringstream ctx;
    int count = 0;
    for (size_t i = 0; i < root.size() && count < 5; i++) {
        const JsonValue* it = root.at(i);
        if (!it) continue;
        std::string title = it->find("title") ? it->find("title")->asString() : "";
        std::string date = it->find("date") ? it->find("date")->asString() : "";
        std::string content = it->find("content") ? it->find("content")->asString() : "";
        if (content.size() > 800) content = content.substr(0, 800) + "...";
        count++;
        ctx << "【" << date << "】" << title << "\n" << content << "\n\n";
    }
    return env->NewStringUTF(SanitizeUTF8(ctx.str()).c_str());
}


// 组装 system 提示：有检索上下文 → 要求引用具体信息；没有 → 普通校园助手
static std::string BuildSystemPrompt(const std::string& context) {
    if (!context.empty()) {
        return "你是东南大学校园智能助手。请优先引用下面【相关资讯】中的具体信息回答"
               "（日期、时间、地点、对象、截止日期、报名方式等）；"
               "资讯里没有的信息请如实说明，不要编造。\n\n"
               "=== 相关资讯 ===\n" + context + "\n=== 资讯结束 ===";
    }
    return "你是东南大学校园智能助手，回答校园相关问题。如果用户的问题涉及具体的"
           "教务通知、讲座或实践信息，请告知用户可以在资讯页查看，不要凭空编造细节。";
}


// 构建 DeepSeek 聊天请求 JSON
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_seu_1agent_NativeBridge_buildLlmRequest(
        JNIEnv* env, jobject, jstring jPrompt, jstring jContext, jstring jModel, jstring jTools) {
    std::string prompt = jstr(env, jPrompt);
    std::string context = jstr(env, jContext);
    std::string model = jstr(env, jModel);
    std::string toolsJson = jstr(env, jTools);
    if (model.empty()) model = "deepseek-chat";

    std::string sys = BuildSystemPrompt(context);

    std::ostringstream ss;
    ss << "{\"model\":\"" << model << "\",\"messages\":[";
    ss << "{\"role\":\"system\",\"content\":\"" << Json_Convert(sys) << "\"}";
    for (const auto& [role, content] : g_chatHistory) {
        ss << ",{\"role\":\"" << role << "\",\"content\":\"" << Json_Convert(content) << "\"}";
    }
    // 本次提问已在 sendMessage 时写入历史，这里不再重复追加
    ss << "],\"stream\":true";
    if (!toolsJson.empty()) {
        ss << ",\"tools\":" << toolsJson << ",\"tool_choice\":\"auto\"";
    }
    ss << "}";
    return env->NewStringUTF(SanitizeUTF8(ss.str()).c_str());
}


// 工具调用续轮请求：assistant(tool_calls 数组) + tool(结果数组) → 模型生成最终回答
// thinking 模型必须把上轮的 reasoning_content 一并回传，否则 400
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_seu_1agent_NativeBridge_buildLlmToolRequest(
        JNIEnv* env, jobject, jstring jContext, jstring jModel, jstring jTools,
        jstring jCalls, jstring jResults, jstring jReasoning) {
    std::string context = jstr(env, jContext);
    std::string model = jstr(env, jModel);
    std::string toolsJson = jstr(env, jTools);
    JsonValue calls = JsonValue::parse(jstr(env, jCalls));     // [{"id":..,"name":..,"arguments":..},...]
    JsonValue results = JsonValue::parse(jstr(env, jResults)); // [{"id":..,"content":..},...]
    std::string reasoning = jstr(env, jReasoning);
    if (model.empty()) model = "deepseek-chat";

    static std::atomic<int> g_callSeq{0};

    std::string sys = BuildSystemPrompt(context);
    std::ostringstream ss;
    ss << "{\"model\":\"" << model << "\",\"messages\":[";
    ss << "{\"role\":\"system\",\"content\":\"" << Json_Convert(sys) << "\"}";
    for (const auto& [role, content] : g_chatHistory) {
        ss << ",{\"role\":\"" << role << "\",\"content\":\"" << Json_Convert(content) << "\"}";
    }
    // assistant 消息：回传思考内容 + 全部工具调用
    ss << ",{\"role\":\"assistant\"";
    if (!reasoning.empty()) {
        ss << ",\"reasoning_content\":\"" << Json_Convert(reasoning) << "\"";
    }
    ss << ",\"content\":null,\"tool_calls\":[";
    for (size_t i = 0; i < calls.size(); i++) {
        const JsonValue* c = calls.at(i);
        const JsonValue* id = c ? c->find("id") : nullptr;
        const JsonValue* name = c ? c->find("name") : nullptr;
        const JsonValue* args = c ? c->find("arguments") : nullptr;
        std::string idS = (id && !id->asString().empty())
                ? id->asString() : ("call_" + std::to_string(++g_callSeq));
        if (i > 0) ss << ",";
        ss << "{\"id\":\"" << Json_Convert(idS) << "\",\"type\":\"function\",\"function\":{"
           << "\"name\":\"" << Json_Convert(name ? name->asString() : "") << "\",\"arguments\":\""
           << Json_Convert(args ? args->asString() : "{}") << "\"}}";
    }
    ss << "]}";
    // 工具执行结果消息（每条对应一个 tool_call_id）
    for (size_t i = 0; i < results.size(); i++) {
        const JsonValue* r = results.at(i);
        const JsonValue* rid = r ? r->find("id") : nullptr;
        const JsonValue* rcontent = r ? r->find("content") : nullptr;
        ss << ",{\"role\":\"tool\",\"tool_call_id\":\""
           << Json_Convert(rid ? rid->asString() : "") << "\",\"content\":\""
           << Json_Convert(rcontent ? rcontent->asString() : "") << "\"}";
    }
    ss << "],\"stream\":true";
    if (!toolsJson.empty()) {
        ss << ",\"tools\":" << toolsJson << ",\"tool_choice\":\"auto\"";
    }
    ss << "}";
    return env->NewStringUTF(SanitizeUTF8(ss.str()).c_str());
}


// 解析 DeepSeek 非流式响应，提取回复文本
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_seu_1agent_NativeBridge_parseLlmReply(JNIEnv* env, jobject, jstring jResp) {
    JsonValue root = JsonValue::parse(jstr(env, jResp));
    const JsonValue* choices = root.find("choices");
    if (!choices || choices->size() == 0) return env->NewStringUTF("[无回复]");
    const JsonValue* msg = choices->at(0)->find("message");
    const JsonValue* content = msg ? msg->find("content") : nullptr;
    return env->NewStringUTF(SanitizeUTF8(content ? content->asString() : "[空回复]").c_str());
}


//  解析 SSE 流式的一行，返回增量文本
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


//  解析 SSE 流式一行中的工具调用增量，返回 {"id":..,"name":..,"arguments":..}
//  DeepSeek 会把工具调用的 id/name 一次性下发，arguments 分片下发，这里原样返回让 Java 拼接
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

    // 组装合法 JSON（避免尾逗号）
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


//  解析 SSE 流式一行中的思考增量（thinking 模型：deepseek-v4-flash / reasoner 等）
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


// ---------- 聊天历史（多轮对话） ----------
extern "C" JNIEXPORT void JNICALL
Java_com_example_seu_1agent_NativeBridge_addChatMessage(
        JNIEnv* env, jobject, jstring jText, jboolean isUser) {
    std::string text = jstr(env, jText);
    g_chatHistory.push_back({isUser ? "user" : "assistant", text});
    if (g_chatHistory.size() > 20) g_chatHistory.erase(g_chatHistory.begin());  // 保留最近 20 条
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_seu_1agent_NativeBridge_clearChatHistory(JNIEnv*, jobject) {
    g_chatHistory.clear();
}