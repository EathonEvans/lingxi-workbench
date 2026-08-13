package com.lingxi.workbench.chat.memory.model;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LLM 提取的记忆数据（用户画像 / 交互记录实体）的不可变有界视图。
 *
 * <p>模型提取的字段是开放集合，不适合硬编码为固定业务字段；
 * 但对 key 数量、key 长度、值长度和序列化总长设置上限，
 * 防止模型输出失控或注入超长内容进入提示词与存储。</p>
 */
@Slf4j
public final class MemoryData {

    /** 单个记忆数据允许的最大字段数。 */
    public static final int MAX_KEYS = 50;
    /** 字段名最大长度，超长字段直接丢弃。 */
    public static final int MAX_KEY_LENGTH = 64;
    /** 字段值最大长度，超长值截断。 */
    public static final int MAX_VALUE_LENGTH = 512;
    /** 序列化进入提示词的最大总长度。 */
    public static final int MAX_SERIALIZED_LENGTH = 4096;

    private final Map<String, String> values;

    private MemoryData(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    /** 空数据。 */
    public static MemoryData empty() {
        return new MemoryData(Map.of());
    }

    /** 从通用 Map 构造（数据库 JSONB 或模型解析产物），按边界规则裁剪。 */
    public static MemoryData from(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return empty();
        }
        Map<String, String> bounded = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (bounded.size() >= MAX_KEYS) {
                log.warn("记忆数据字段数超过上限 {}，已裁剪", MAX_KEYS);
                break;
            }
            String key = entry.getKey();
            if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
                continue;
            }
            String value = entry.getValue() == null ? "" : String.valueOf(entry.getValue());
            if (value.length() > MAX_VALUE_LENGTH) {
                value = value.substring(0, MAX_VALUE_LENGTH);
            }
            bounded.put(key, value);
        }
        return new MemoryData(bounded);
    }

    /**
     * 从模型返回的 JSON 对象构造；非法 JSON 返回空数据。
     */
    public static MemoryData fromJson(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        try {
            JSONObject object = JSON.parseObject(json);
            return object == null ? empty() : from(object);
        } catch (RuntimeException exception) {
            log.warn("记忆数据 JSON 解析失败，已忽略提取结果");
            return empty();
        }
    }

    /**
     * 与另一份数据合并（后者覆盖同名 key），返回新实例。
     */
    public MemoryData merge(MemoryData other) {
        if (other.isEmpty()) {
            return this;
        }
        Map<String, String> merged = new LinkedHashMap<>(values);
        merged.putAll(other.values);
        return new MemoryData(merged);
    }

    /**
     * 有界序列化为 JSON，超过总长上限时截断，防止超长内容进入提示词。
     */
    public String toPromptJson() {
        String json = JSON.toJSONString(values);
        if (json.length() > MAX_SERIALIZED_LENGTH) {
            return json.substring(0, MAX_SERIALIZED_LENGTH);
        }
        return json;
    }

    /** 返回存储用 Map 副本（值统一为字符串）。 */
    public Map<String, Object> asObjectMap() {
        return new LinkedHashMap<>(values);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }
}
