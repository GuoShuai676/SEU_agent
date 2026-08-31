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

    // 各类型对应的原始值
    std::string strVal;                       // String
    double      numVal = 0;                   // Number
    bool        boolVal = false;              // Bool
    std::vector<JsonValue>                 arr;  // Array
    std::map<std::string, JsonValue>      obj;  // Object

    // ---------- 工厂方法 ----------
    static JsonValue parse(const std::string& text);

    // ---------- 查询方法 ----------
    // 按 key 查找子节点（仅 Object 类型有效），找不到返回 nullptr
    const JsonValue* find(const std::string& key) const;

    // 按 index 访问数组元素（仅 Array 类型有效）
    const JsonValue* at(size_t index) const;

    // 取标量值（类型不匹配时返回默认值）
    std::string asString() const;   // String → 原值；其他 → 空串
    int         asInt()    const;   // Number → 截断为 int
    double      asDouble() const;   // Number → 原值
    bool        asBool()   const;   // Bool → 原值；Number → 非 0 为 true
    size_t      size()     const;   // Array/Object 元素个数

    // ---------- 判断 ----------
    bool isNull()   const { return type == JsonType::Null; }
    bool isObject() const { return type == JsonType::Object; }
    bool isArray()  const { return type == JsonType::Array; }
    bool isString() const { return type == JsonType::String; }
};

#endif // CAMPUS_AGENT_JSON_PARSER_H
