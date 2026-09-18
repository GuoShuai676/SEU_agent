#ifndef CAMPUS_AGENT_JSON_PARSER_H
#define CAMPUS_AGENT_JSON_PARSER_H

#include <string>
#include <vector>
#include <map>
#include <cstdint>


enum class JsonType {
    Null,
    Bool,
    Number,
    String,
    Array,
    Object
};


struct JsonValue {
    JsonType type = JsonType::Null;

    std::string strVal;                       // String
    double      numVal = 0;                   // Number
    bool        boolVal = false;              // Bool
    std::vector<JsonValue>                 arr;  // Array
    std::map<std::string, JsonValue>      obj;  // Object

    static JsonValue parse(const std::string& text);

    const JsonValue* find(const std::string& key) const;

    const JsonValue* at(size_t index) const;

    std::string asString() const;   // String → 原值；其他 → 空串
    int         asInt()    const;   // Number → 截断为 int
    double      asDouble() const;   // Number → 原值
    bool        asBool()   const;   // Bool → 原值；Number → 非 0 为 true
    size_t      size()     const;   // Array/Object 元素个数

    bool isNull()   const { return type == JsonType::Null; }
    bool isObject() const { return type == JsonType::Object; }
    bool isArray()  const { return type == JsonType::Array; }
    bool isString() const { return type == JsonType::String; }
};

#endif // CAMPUS_AGENT_JSON_PARSER_H
