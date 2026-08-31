#include <jni.h>
#include <string>
#include <sstream>
#include <vector>
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