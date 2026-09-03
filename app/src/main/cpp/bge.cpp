// ===================================================================
//  bge.cpp —— 本地语义嵌入（C++ 实现，JNI 暴露给 Java）
//
//  分工（与项目"逻辑放 C++"一致）：
//    1. BERT WordPiece 分词器（中文逐字 + 英文子词）
//    2. 中文按句切分（。！？；… 换行）
//    3. ONNX Runtime C API 跑 bge-small-zh-v1.5 INT8，取 CLS 向量归一化
//
//  JNI 接口：
//    initBge(modelPath, vocabPath) -> boolean  加载模型（线程安全，幂等）
//    embedText(text) -> float[512]             文本 → 归一化向量
//    splitSentences(text) -> String[]          中文按句切分
// ===================================================================

#include <jni.h>
#include <android/log.h>

#include <string>
#include <vector>
#include <array>
#include <unordered_map>
#include <memory>
#include <fstream>
#include <cmath>
#include <sstream>

#include "onnxruntime_cxx_api.h"

#define LOG_TAG "seu_bge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ---------------------------------------------------------------
//  UTF-8 工具
// ---------------------------------------------------------------

// 解码 s[i] 处的一个 UTF-8 字符
static bool DecodeUtf8(const std::string& s, size_t i, uint32_t& cp, int& len) {
    unsigned char c = (unsigned char) s[i];
    if (c < 0x80) { cp = c; len = 1; return true; }
    if      ((c & 0xE0) == 0xC0) { cp = c & 0x1F; len = 2; }
    else if ((c & 0xF0) == 0xE0) { cp = c & 0x0F; len = 3; }
    else if ((c & 0xF8) == 0xF0) { cp = c & 0x07; len = 4; }
    else return false;
    for (int k = 1; k < len; k++) {
        if (i + k >= s.size() || ((unsigned char) s[i + k] & 0xC0) != 0x80) return false;
        cp = (cp << 6) | ((unsigned char) s[i + k] & 0x3F);
    }
    return true;
}

// 是否 CJK 汉字（含扩展 A 与兼容区）
static bool IsCjk(uint32_t cp) {
    return (cp >= 0x3400 && cp <= 0x4DBF)
        || (cp >= 0x4E00 && cp <= 0x9FFF)
        || (cp >= 0xF900 && cp <= 0xFAFF);
}

static std::string Trim(const std::string& s) {
    size_t b = 0, e = s.size();
    while (b < e && isspace((unsigned char) s[b])) b++;
    while (e > b && isspace((unsigned char) s[e - 1])) e--;
    return s.substr(b, e - b);
}

// ---------------------------------------------------------------
//  BERT WordPiece 分词器（与模型配置一致：不转小写、中文逐字、512 上限）
// ---------------------------------------------------------------

class BgeTokenizer {
public:
    static const int CLS_ID = 101;
    static const int SEP_ID = 102;
    static const int UNK_ID = 100;
    static const int MAX_LEN = 512;

    void load(const std::vector<std::string>& lines) {
        vocab_.clear();
        maxWordLen_ = 0;
        int id = 0;
        for (const std::string& raw : lines) {
            std::string t = Trim(raw);
            if (t.empty()) continue;
            vocab_[t] = id++;
            if (t.size() > (size_t) maxWordLen_) maxWordLen_ = (int) t.size();
        }
    }

    // 编码为模型输入：[0]=input_ids [1]=attention_mask [2]=token_type_ids，均 512
    std::array<std::vector<int64_t>, 3> encode(const std::string& text) {
        std::vector<std::string> pieces = WordPiece(BasicTokenize(text));
        std::vector<int64_t> ids(MAX_LEN, 0), mask(MAX_LEN, 0), types(MAX_LEN, 0);
        size_t pos = 0;
        ids[pos++] = CLS_ID;
        for (const std::string& p : pieces) {
            if (pos >= (size_t) MAX_LEN - 1) break;
            auto it = vocab_.find(p);
            ids[pos++] = (it != vocab_.end()) ? it->second : UNK_ID;
        }
        ids[pos] = SEP_ID;
        for (size_t i = 0; i <= pos; i++) mask[i] = 1;
        return {ids, mask, types};
    }

private:
    std::unordered_map<std::string, int> vocab_;
    int maxWordLen_ = 0;

    // 将暂存的英文、数字等内容按空格拆开，加入最终 token 列表。
    void FlushBuffer(std::string& buffer, std::vector<std::string>& tokens) {
        std::istringstream words(buffer);
        std::string word;
        while (words >> word) {
            tokens.push_back(word);
        }
        buffer.clear();
    }

    // 基础分词
    std::vector<std::string> BasicTokenize(const std::string& text) {
        // 清洗：去除与含义无关的控制字符以及decodeUTF8函数读取表示受损的字符
        std::string cleaned;
        cleaned.reserve(text.size());
        for (size_t i = 0; i < text.size();) {
            uint32_t cp; int len;
            if (!DecodeUtf8(text, i, cp, len)) { cleaned += ' '; i++; continue; }
            if (cp < 0x20 || cp == 0x7f) cleaned += ' ';
            else cleaned.append(text, i, len);
            i += len;
        }

        std::vector<std::string> tokens;
        std::string nonChineseBuffer;
        for (size_t i = 0; i < cleaned.size();) {
            uint32_t cp; int len;
            if (!DecodeUtf8(cleaned, i, cp, len)) { i++; continue; }
            if (IsCjk(cp)) {
                // 有汉字先处理缓冲区
                FlushBuffer(nonChineseBuffer, tokens);
                tokens.push_back(cleaned.substr(i, len));
            } else {
                // 非中文先塞进缓冲区
                nonChineseBuffer.append(cleaned, i, len);
            }
            i += len;
        }
        //最后所有的非中文数据弹出
        FlushBuffer(nonChineseBuffer, tokens);
        return tokens;
    }

    // 最长token匹配
    std::vector<std::string> WordPiece(const std::vector<std::string>& tokens) {
        std::vector<std::string> out;
        for (const std::string& token : tokens) {
            //如果大小超过训练时的最大，直接返回UNK
            if (token.size() > (size_t) maxWordLen_) { out.push_back("[UNK]"); continue; }
            std::vector<std::string> pieces;
            std::string cur = token;
            bool first = true, ok = true;
            while (!cur.empty()) {
                int limitBase = first ? maxWordLen_ : (maxWordLen_ > 2 ? maxWordLen_ - 2 : maxWordLen_);
                size_t limit = std::min(cur.size(), (size_t) limitBase);
                std::string found;
                bool hit = false;
                for (size_t len = limit; len > 0; len--) {
                    std::string key = first ? cur.substr(0, len) : "##" + cur.substr(0, len);
                    if (vocab_.count(key)) { found = key; hit = true; break; }
                }
                if (!hit) { ok = false; break; }
                pieces.push_back(found);
                cur = cur.substr(found.size() - (first ? 0 : 2));
                first = false;
            }
            if (ok) out.insert(out.end(), pieces.begin(), pieces.end());
            else out.push_back("[UNK]");
        }
        return out;
    }
};

// ---------------------------------------------------------------
//  中文按句切分：。！？；… 以及换行 作为句子边界
// ---------------------------------------------------------------

static std::vector<std::string> SplitSentences(const std::string& text) {
    std::vector<std::string> out;
    std::string cur;
    auto flush = [&](const std::string& s) {
        std::string t = Trim(s);
        if (t.size() >= 2) out.push_back(t);   // 太短的碎片丢弃
    };
    for (size_t i = 0; i < text.size();) {
        uint32_t cp; int len;
        if (!DecodeUtf8(text, i, cp, len)) { cur += text[i]; i++; continue; }
        cur.append(text, i, len);
        // 。(0x3002) ！(0xFF01) ？(0xFF1F) ；(0xFF1B) …(0x2026) 换行(0x0A)
        if (cp == 0x3002 || cp == 0xFF01 || cp == 0xFF1F || cp == 0xFF1B || cp == 0x2026 || cp == 0x0A) {
            flush(cur);
            cur.clear();
        }
        i += len;
    }
    flush(cur);
    return out;
}

//bge state

namespace {

struct BgeState {
    Ort::Env env{ORT_LOGGING_LEVEL_WARNING, "seu_agent"};
    Ort::MemoryInfo memInfo = Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault);
    std::unique_ptr<Ort::Session> session;
    BgeTokenizer tokenizer;
    bool ready = false;
};

BgeState& State() {
    static BgeState s;
    return s;
}

} // namespace

// ---------------------------------------------------------------
//  JNI 接口
// ---------------------------------------------------------------

static std::string jstr(JNIEnv* env, jstring j) {
    if (!j) return "";
    const char* c = env->GetStringUTFChars(j, nullptr);
    std::string s(c ? c : "");
    env->ReleaseStringUTFChars(j, c);
    return s;
}

// 加载模型 + 词表
extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_seu_1agent_NativeBridge_initBge(JNIEnv* env, jobject, jstring jModelPath, jstring jVocabPath) {
    std::string modelPath = jstr(env, jModelPath);
    std::string vocabPath = jstr(env, jVocabPath);
    BgeState& st = State();
    try {
        // 词表
        std::vector<std::string> lines;
        {
            std::ifstream f(vocabPath);
            if (!f.is_open()) { LOGE("vocab open failed: %s", vocabPath.c_str()); return JNI_FALSE; }
            std::string line;
            while (std::getline(f, line)) {
                std::string t = Trim(line);
                if (!t.empty()) lines.push_back(t);
            }
        }
        st.tokenizer.load(lines);
        // 会话
        Ort::SessionOptions opts;
        opts.SetIntraOpNumThreads(2);
        st.session = std::make_unique<Ort::Session>(st.env, modelPath.c_str(), opts);
        st.ready = true;
        return JNI_TRUE;
    } catch (const std::exception& e) {
        LOGE("initBge failed: %s", e.what());
        st.ready = false;
        st.session.reset();
        return JNI_FALSE;
    }
}

// 文本 → float[512]
extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_example_seu_1agent_NativeBridge_embedText(JNIEnv* env, jobject, jstring jText) {
    BgeState& st = State();
    if (!st.ready || !st.session) return nullptr;
    std::string text = jstr(env, jText);
    try {
        auto enc = st.tokenizer.encode(text);
        std::vector<int64_t> ids = enc[0];    // 非 const：CreateTensor<T> 需要 T* 指针
        std::vector<int64_t> mask = enc[1];
        std::vector<int64_t> types = enc[2];
        int64_t shape[2] = {1, BgeTokenizer::MAX_LEN};

        std::vector<Ort::Value> inputs;
        inputs.emplace_back(Ort::Value::CreateTensor<int64_t>(st.memInfo, ids.data(), ids.size(), shape, 2));
        inputs.emplace_back(Ort::Value::CreateTensor<int64_t>(st.memInfo, mask.data(), mask.size(), shape, 2));
        inputs.emplace_back(Ort::Value::CreateTensor<int64_t>(st.memInfo, types.data(), types.size(), shape, 2));
        const char* inputNames[] = {"input_ids", "attention_mask", "token_type_ids"};
        const char* outputNames[] = {"last_hidden_state"};

        Ort::RunOptions ro;
        std::vector<Ort::Value> outputs =
                st.session->Run(ro, inputNames, inputs.data(), 3, outputNames, 1);
        float* data = outputs[0].GetTensorMutableData<float>();  // [1,512,512]，CLS = 前 512 个

        // L2 归一化
        double sum = 0;
        for (int i = 0; i < BgeTokenizer::MAX_LEN; i++) sum += (double) data[i] * data[i];
        double norm = std::sqrt(sum);

        jfloatArray out = env->NewFloatArray(BgeTokenizer::MAX_LEN);
        if (!out) return nullptr;
        std::vector<float> vec(BgeTokenizer::MAX_LEN);
        for (int i = 0; i < BgeTokenizer::MAX_LEN; i++) {
            vec[i] = (float) (norm > 1e-8 ? data[i] / norm : data[i]);
        }
        env->SetFloatArrayRegion(out, 0, BgeTokenizer::MAX_LEN, vec.data());
        return out;
    } catch (const std::exception& e) {
        LOGE("embed failed: %s", e.what());
        return nullptr;
    }
}

// 中文按句切分
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_seu_1agent_NativeBridge_splitSentences(JNIEnv* env, jobject, jstring jText) {
    std::string text = jstr(env, jText);
    std::vector<std::string> sents = SplitSentences(text);
    jclass strCls = env->FindClass("java/lang/String");
    if (!strCls) return nullptr;
    jobjectArray arr = env->NewObjectArray((jsize) sents.size(), strCls, nullptr);
    for (size_t i = 0; i < sents.size(); i++) {
        env->SetObjectArrayElement(arr, (jsize) i, env->NewStringUTF(sents[i].c_str()));
    }
    return arr;
}
