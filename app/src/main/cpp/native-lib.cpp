#include <jni.h>
#include <string>
#include <sstream>
#include <vector>
#include <utility>
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
            default:
                out+=c;
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
        if((c>'a'&&c<'z')||(c>'A'&&c<'Z')||c=='~'||c=='-'||c=='.'||(c>'0'&&c<'9')||c=='_')
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
    return env->NewStringUTF(ctx.str().c_str());
}


// 构建 DeepSeek 聊天请求 JSON
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_seu_1agent_NativeBridge_buildLlmRequest(
        JNIEnv* env, jobject, jstring jPrompt, jstring jContext, jstring jModel) {
    std::string prompt = jstr(env, jPrompt);
    std::string context = jstr(env, jContext);
    std::string model = jstr(env, jModel);
    if (model.empty()) model = "deepseek-chat";

    std::string sys = "你是东南大学校园智能助手，请基于以下检索到的资讯回答；"
                      "无关的内容请忽略，如果没有相关信息请如实告知。\n\n"
                      "=== 相关资讯 ===\n" + context + "\n=== 资讯结束 ===";

    std::ostringstream ss;
    ss << "{\"model\":\"" << model << "\",\"messages\":[";
    ss << "{\"role\":\"system\",\"content\":\"" << Json_Convert(sys) << "\"}";
    for (const auto& [role, content] : g_chatHistory) {
        ss << ",{\"role\":\"" << role << "\",\"content\":\"" << Json_Convert(content) << "\"}";
    }
    ss << ",{\"role\":\"user\",\"content\":\"" << Json_Convert(prompt) << "\"}";
    ss << "],\"stream\":true}";
    return env->NewStringUTF(ss.str().c_str());
}


// 解析 DeepSeek 非流式响应，提取回复文本
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_seu_1agent_NativeBridge_parseLlmReply(JNIEnv* env, jobject, jstring jResp) {
    JsonValue root = JsonValue::parse(jstr(env, jResp));
    const JsonValue* choices = root.find("choices");
    if (!choices || choices->size() == 0) return env->NewStringUTF("[无回复]");
    const JsonValue* msg = choices->at(0)->find("message");
    const JsonValue* content = msg ? msg->find("content") : nullptr;
    return env->NewStringUTF(content ? content->asString().c_str() : "[空回复]");
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
    return env->NewStringUTF(content ? content->asString().c_str() : "");
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