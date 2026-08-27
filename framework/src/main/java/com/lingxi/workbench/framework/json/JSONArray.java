package com.lingxi.workbench.framework.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 项目内使用的可变 JSON 数组。
 *
 * <p>数组保留 List 的顺序和 Stream 能力，并提供业务代码常用的对象读取和
 * 强类型转换方法。具体的文本解析仍由 {@link JSON} 统一完成。</p>
 */
public class JSONArray extends ArrayList<Object> {

    private static final long serialVersionUID = 1L;

    public JSONArray() {
    }

    public JSONArray(Collection<?> values) {
        if (values != null) {
            addAll(values);
        }
    }

    /** 使用可变参数创建动态 JSON 数组。 */
    public static JSONArray of(Object... values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (Object value : values) {
                array.add(value);
            }
        }
        return array;
    }

    /** 将 JSON 文本解析为动态数组。 */
    public static JSONArray parseArray(String json) {
        return JSON.parseArray(json);
    }

    public JSONObject getJSONObject(int index) {
        Object value = get(index);
        return value == null ? null : JSONObject.from(value);
    }

    public String getString(int index) {
        Object value = get(index);
        return value == null ? null : String.valueOf(value);
    }

    public Boolean getBoolean(int index) {
        Object value = get(index);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public Long getLong(int index) {
        Object value = get(index);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new JSONException("JSON 数组元素不是合法 Long: " + index, exception);
        }
    }

    /** 将数组元素转换为指定的强类型列表。 */
    public <T> List<T> toJavaList(Class<T> type) {
        return JSON.parseArray(JSON.toJSONString(this), type);
    }

    /** 输出数组的 JSON 文本。 */
    public String toJSONString() {
        return JSON.toJSONString(this);
    }

    @Override
    public String toString() {
        return toJSONString();
    }
}
