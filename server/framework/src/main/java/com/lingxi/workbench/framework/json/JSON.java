package com.lingxi.workbench.framework.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 项目统一 JSON 门面。
 *
 * <p>业务模块只依赖本类和 JSONObject/JSONArray，不直接依赖具体 JSON 供应商。
 * 这样替换库时可以集中维护动态对象、枚举、工作流多态和精度保护等契约，避免
 * 在每个业务调用点重复实现转换规则。</p>
 */
public final class JSON {

    private JSON() {
    }

    /** 返回 Web 层和其他 JSON 边界共享的默认 Gson 实例。 */
    public static Gson gson() {
        return GsonJsonSupport.createGson();
    }

    /** 将 Java 对象序列化为紧凑 JSON 文本。 */
    public static String toJSONString(Object value) {
        return toJSONString(value, new JSONWriter.Feature[0]);
    }

    /** 按指定输出特性将 Java 对象序列化为 JSON 文本。 */
    public static String toJSONString(Object value, JSONWriter.Feature... features) {
        try {
            return GsonJsonSupport.createGson(features).toJson(value);
        } catch (JsonParseException | IllegalArgumentException exception) {
            throw new JSONException("JSON 序列化失败", exception);
        }
    }

    /** 将 Java 对象序列化为 UTF-8 JSON 字节数组，供摘要和消息边界使用。 */
    public static byte[] toJSONBytes(Object value, JSONWriter.Feature... features) {
        return toJSONString(value, features).getBytes(StandardCharsets.UTF_8);
    }

    /** 将 JSON 文本解析为动态对象。 */
    public static JSONObject parseObject(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            return (JSONObject) GsonJsonSupport.toJavaValue(element);
        } catch (JsonParseException | ClassCastException exception) {
            throw new JSONException("JSON 对象解析失败", exception);
        }
    }

    /** 将 UTF-8 JSON 字节数组解析为动态对象。 */
    public static JSONObject parseObject(byte[] json) {
        return parseObject(json == null ? null : new String(json, StandardCharsets.UTF_8));
    }

    /** 将 JSON 文本解析为指定 Class。 */
    public static <T> T parseObject(String json, Class<T> type) {
        return parseObject(json, (Type) type);
    }

    /** 将 JSON 文本解析为指定泛型 Type。 */
    public static <T> T parseObject(String json, Type type) {
        if (json == null) {
            return null;
        }
        try {
            return GsonJsonSupport.createGson().fromJson(json, type);
        } catch (JsonParseException | IllegalStateException exception) {
            throw new JSONException("JSON 对象解析失败，目标类型: " + type.getTypeName(), exception);
        }
    }

    /** 将 UTF-8 JSON 字节数组解析为指定 Class。 */
    public static <T> T parseObject(byte[] json, Class<T> type) {
        return parseObject(json == null ? null : new String(json, StandardCharsets.UTF_8), type);
    }

    /** 将 JSON 文本解析为动态数组。 */
    public static JSONArray parseArray(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            return (JSONArray) GsonJsonSupport.toJavaValue(element);
        } catch (JsonParseException | ClassCastException exception) {
            throw new JSONException("JSON 数组解析失败", exception);
        }
    }

    /** 将 JSON 数组解析为指定元素类型的列表。 */
    public static <T> List<T> parseArray(String json, Class<T> elementType) {
        if (json == null) {
            return null;
        }
        Type listType = TypeToken.getParameterized(List.class, elementType).getType();
        return parseObject(json, listType);
    }
}
