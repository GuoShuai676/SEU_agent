
#include "json_parser.h"
#include <sstream>
#include <stdexcept>


static void encodeUtf8(uint32_t cp, std::string& out) {
    if (cp <= 0x7F) {
        out += (char) cp;
    } else if (cp <= 0x7FF) {
        out += (char) (0xC0 | (cp >> 6));
        out += (char) (0x80 | (cp & 0x3F));
    } else if (cp <= 0xFFFF) {
        out += (char) (0xE0 | (cp >> 12));
        out += (char) (0x80 | ((cp >> 6) & 0x3F));
        out += (char) (0x80 | (cp & 0x3F));
    } else {
        out += (char) (0xF0 | (cp >> 18));
        out += (char) (0x80 | ((cp >> 12) & 0x3F));
        out += (char) (0x80 | ((cp >> 6) & 0x3F));
        out += (char) (0x80 | (cp & 0x3F));
    }
}

static int hexDigit(char c) {
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    return -1;
}

class Parser {
public:
    explicit Parser(const std::string& s) : src_(s), pos_(0) {}

    JsonValue parse() {
        skipWs();
        JsonValue v = parseValue();
        skipWs();
        return v;
    }

private:
    const std::string& src_;
    size_t pos_;

    char peek() { return pos_ < src_.size() ? src_[pos_] : '\0'; }
    char next() { return pos_ < src_.size() ? src_[pos_++] : '\0'; }
    bool eof()  { return pos_ >= src_.size(); }

    void skipWs() {
        while (pos_ < src_.size() &&
               (src_[pos_] == ' ' || src_[pos_] == '\t' ||
                src_[pos_] == '\n' || src_[pos_] == '\r'))
            pos_++;
    }

    bool match(char expected) {
        if (peek() == expected) { pos_++; return true; }
        return false;
    }

    JsonValue parseValue() {
        skipWs();
        if (eof()) return {};
        char c = peek();
        switch (c) {
            case '{': return parseObject();
            case '[': return parseArray();
            case '"': return parseString();
            case 't': case 'f': return parseBool();
            case 'n': return parseNull();
            default:
                if (c == '-' || (c >= '0' && c <= '9'))
                    return parseNumber();
                return {};  // 解析失败
        }
    }

    JsonValue parseString() {
        JsonValue v;
        v.type = JsonType::String;
        if (!match('"')) return {};

        std::string out;
        out.reserve(32);
        while (!eof()) {
            char c = next();
            if (c == '"') {
                v.strVal = std::move(out);
                return v;
            }
            if (c == '\\') {
                if (eof()) return {};
                char esc = next();
                switch (esc) {
                    case '"': out += '"'; break;
                    case '\\': out += '\\'; break;
                    case '/': out += '/'; break;
                    case 'b': out += '\b'; break;
                    case 'f': out += '\f'; break;
                    case 'n': out += '\n'; break;
                    case 'r': out += '\r'; break;
                    case 't': out += '\t'; break;
                    case 'u': {
                        uint32_t cp = 0;
                        for (int i = 0; i < 4; i++) {
                            if (eof()) return {};
                            int h = hexDigit(next());
                            if (h < 0) return {};
                            cp = (cp << 4) | (uint32_t) h;
                        }
                        if (cp >= 0xD800 && cp <= 0xDBFF && !eof() && peek() == '\\') {
                            size_t save = pos_;
                            if (src_[pos_++] == '\\' && !eof() && next() == 'u') {
                                uint32_t low = 0;
                                bool ok = true;
                                for (int i = 0; i < 4; i++) {
                                    if (eof()) { ok = false; break; }
                                    int h = hexDigit(next());
                                    if (h < 0) { ok = false; break; }
                                    low = (low << 4) | (uint32_t) h;
                                }
                                if (ok && low >= 0xDC00 && low <= 0xDFFF) {
                                    cp = 0x10000 + ((cp - 0xD800) << 10) + (low - 0xDC00);
                                } else {
                                    pos_ = save;  // 回退
                                }
                            } else {
                                pos_ = save;
                            }
                        }
                        encodeUtf8(cp, out);
                        break;
                    }
                    default:
                        out += esc;
                        break;
                }
            } else {
                out += c;
            }
        }
        return {};  // 未闭合的字符串
    }

    JsonValue parseNumber() {
        std::string s;
        if (peek() == '-') s += next();
        while (!eof()) {
            char c = peek();
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' ||
                c == '+' || c == '-') {
                s += next();
            } else {
                break;
            }
        }
        JsonValue v;
        v.type = JsonType::Number;
        v.numVal = std::stod(s);
        return v;
    }

    JsonValue parseBool() {
        JsonValue v;
        v.type = JsonType::Bool;
        if (src_.compare(pos_, 4, "true") == 0) {
            pos_ += 4;
            v.boolVal = true;
        } else if (src_.compare(pos_, 5, "false") == 0) {
            pos_ += 5;
            v.boolVal = false;
        }
        return v;
    }

    JsonValue parseNull() {
        if (src_.compare(pos_, 4, "null") == 0) {
            pos_ += 4;
        }
        return {};
    }

    JsonValue parseArray() {
        JsonValue v;
        v.type = JsonType::Array;
        match('[');
        skipWs();
        if (peek() == ']') { next(); return v; }  // 空数组

        while (!eof()) {
            v.arr.push_back(parseValue());
            skipWs();
            if (match(',')) { skipWs(); continue; }
            if (match(']')) break;
            break;
        }
        return v;
    }

    JsonValue parseObject() {
        JsonValue v;
        v.type = JsonType::Object;
        match('{');
        skipWs();
        if (peek() == '}') { next(); return v; }  // 空对象

        while (!eof()) {
            skipWs();
            if (peek() != '"') break;
            JsonValue key = parseString();
            if (key.type != JsonType::String) break;
            skipWs();
            if (!match(':')) break;
            JsonValue val = parseValue();
            v.obj[key.strVal] = std::move(val);
            skipWs();
            if (match(',')) { skipWs(); continue; }
            if (match('}')) break;
            break;
        }
        return v;
    }
};


JsonValue JsonValue::parse(const std::string& text) {
    Parser p(text);
    return p.parse();
}

const JsonValue* JsonValue::find(const std::string& key) const {
    if (type != JsonType::Object) return nullptr;
    auto it = obj.find(key);
    if (it == obj.end()) return nullptr;
    return &it->second;
}

const JsonValue* JsonValue::at(size_t index) const {
    if (type != JsonType::Array || index >= arr.size()) return nullptr;
    return &arr[index];
}

std::string JsonValue::asString() const {
    return type == JsonType::String ? strVal : "";
}

int JsonValue::asInt() const {
    return type == JsonType::Number ? (int) numVal : 0;
}

double JsonValue::asDouble() const {
    return type == JsonType::Number ? numVal : 0;
}

bool JsonValue::asBool() const {
    if (type == JsonType::Bool) return boolVal;
    if (type == JsonType::Number) return numVal != 0;
    return false;
}

size_t JsonValue::size() const {
    if (type == JsonType::Array) return arr.size();
    if (type == JsonType::Object) return obj.size();
    return 0;
}
