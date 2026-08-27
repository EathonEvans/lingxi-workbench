package com.lingxi.workbench.framework.json;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 项目内使用的可变 JSON 对象。
 *
 * <p>它保留 Map 的插入顺序，适合承载 Tool Schema、模型扩展参数和外部网关
 * 返回的动态结构。所有 JSON 文本转换都委托给 {@link JSON}，从而保证动态对象
 * 与强类型对象使用相同的 Gson 配置。</p>
 */
public class JSONObject extends LinkedHashMap<String, Object> {

    @Serial
    private static final long serialVersionUID = 1L;

    public JSONObject() {
    }

    public JSONObject(Map<String, ?> values) {
        if (values != null) {
            putAll(values);
        }
    }

    /** 使用交替的 key/value 参数创建动态 JSON 对象。 */
    public static JSONObject of(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return new JSONObject();
        }
        if ((keyValues.length & 1) != 0) {
            throw new JSONException("JSONObject.of 必须接收成对的 key/value 参数");
        }
        JSONObject object = new JSONObject();
        for (int index = 0; index < keyValues.length; index += 2) {
            Object key = keyValues[index];
            if (key == null) {
                throw new JSONException("JSONObject.of 的 key 不能为 null");
            }
            object.put(String.valueOf(key), keyValues[index + 1]);
        }
        return object;
    }

    /** 将 JSON 文本解析为动态对象。 */
    public static JSONObject parseObject(String json) {
        return JSON.parseObject(json);
    }

    /** 将 Map 或普通 Java 对象转换为动态对象。 */
    public static JSONObject from(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JSONObject object) {
            return object;
        }
        if (value instanceof Map<?, ?> map) {
            JSONObject object = new JSONObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                object.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return object;
        }
        return JSON.parseObject(JSON.toJSONString(value));
    }

    public String getString(String key) {
        Object value = get(key);
        return value == null ? null : String.valueOf(value);
    }

    public Integer getInteger(String key) {
        Number number = getNumber(key);
        return number == null ? null : number.intValue();
    }

    public int getIntValue(String key) {
        Integer value = getInteger(key);
        return value == null ? 0 : value;
    }

    public Long getLong(String key) {
        Number number = getNumber(key);
        return number == null ? null : number.longValue();
    }

    public Double getDouble(String key) {
        Number number = getNumber(key);
        return number == null ? null : number.doubleValue();
    }

    public double getDoubleValue(String key) {
        Double value = getDouble(key);
        return value == null ? 0D : value;
    }

    public long getLongValue(String key) {
        Long value = getLong(key);
        return value == null ? 0L : value;
    }

    public Boolean getBoolean(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    public boolean getBooleanValue(String key) {
        Boolean value = getBoolean(key);
        return Boolean.TRUE.equals(value);
    }

    public JSONObject getJSONObject(String key) {
        Object value = get(key);
        return value == null ? null : JSONObject.from(value);
    }

    public JSONArray getJSONArray(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof JSONArray array) {
            return array;
        }
        if (value instanceof Iterable<?> iterable) {
            JSONArray array = new JSONArray();
            for (Object item : iterable) {
                array.add(item);
            }
            return array;
        }
        return JSON.parseArray(JSON.toJSONString(value));
    }

    /** 按指定类型读取动态字段，供外部 JSON 响应中的时间等强类型值使用。 */
    public <T> T getObject(String key, Class<T> type) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return JSON.parseObject(JSON.toJSONString(value), type);
    }

    /** 将动态对象转换为指定的强类型对象。 */
    public <T> T toJavaObject(Class<T> type) {
        return JSON.parseObject(JSON.toJSONString(this), type);
    }

    /** 输出动态对象的 JSON 文本。 */
    public String toJSONString() {
        return JSON.toJSONString(this);
    }

    private Number getNumber(String key) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number;
        }
        try {
            return new java.math.BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new JSONException("JSON 字段不是合法数字: " + key, exception);
        }
    }

    @Override
    public String toString() {
        return toJSONString();
    }
}
