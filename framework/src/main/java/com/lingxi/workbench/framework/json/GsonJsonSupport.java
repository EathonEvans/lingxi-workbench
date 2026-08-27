package com.lingxi.workbench.framework.json;

import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.LongSerializationPolicy;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.lingxi.workbench.framework.json.annotation.JSONField;
import com.lingxi.workbench.framework.json.annotation.JSONType;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Gson 适配层的集中配置入口。
 *
 * <p>项目既有代码同时处理强类型快照和动态 JSON 对象，单纯把依赖换成 Gson
 * 会丢失节点多态、字段别名、枚举业务值和 Long 精度保护。因此这里把这些
 * 已存在的序列化契约集中实现，并让 JSONB、缓存、消息和 MVC 复用同一个 Gson
 * 配置来源。</p>
 */
final class GsonJsonSupport {

    private GsonJsonSupport() {
    }

    static Gson createGson(JSONWriter.Feature... features) {
        boolean prettyFormat = contains(features, JSONWriter.Feature.PrettyFormat);
        boolean writeLongAsString = contains(features, JSONWriter.Feature.WriteLongAsString);

        GsonBuilder builder = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd HH:mm:ss")
                .setFieldNamingStrategy(new JSONFieldNamingStrategy())
                .registerTypeHierarchyAdapter(JSONObject.class, new JSONObjectTypeAdapter())
                .registerTypeHierarchyAdapter(JSONArray.class, new JSONArrayTypeAdapter())
                .registerTypeAdapter(OffsetDateTime.class,
                        new StringValueTypeAdapter<>(OffsetDateTime::parse, OffsetDateTime::toString))
                .registerTypeAdapter(Instant.class,
                        new StringValueTypeAdapter<>(Instant::parse, Instant::toString))
                .registerTypeAdapter(LocalDate.class,
                        new StringValueTypeAdapter<>(LocalDate::parse, LocalDate::toString))
                .registerTypeAdapter(Duration.class,
                        new StringValueTypeAdapter<>(Duration::parse, Duration::toString))
                .registerTypeAdapterFactory(new StrictEnumTypeAdapterFactory())
                .registerTypeAdapterFactory(new PolymorphicJSONTypeAdapterFactory())
                .registerTypeAdapterFactory(new JSONFieldFeatureAdapterFactory());

        if (prettyFormat) {
            builder.setPrettyPrinting();
        }
        if (writeLongAsString) {
            builder.setLongSerializationPolicy(LongSerializationPolicy.STRING);
        }
        return builder.create();
    }

    static Object toJavaValue(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            JSONObject object = new JSONObject();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                object.put(entry.getKey(), toJavaValue(entry.getValue()));
            }
            return object;
        }
        if (element.isJsonArray()) {
            JSONArray array = new JSONArray();
            for (JsonElement item : element.getAsJsonArray()) {
                array.add(toJavaValue(item));
            }
            return array;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isString()) {
            return primitive.getAsString();
        }
        return primitive.getAsNumber();
    }

    private static boolean contains(JSONWriter.Feature[] features, JSONWriter.Feature expected) {
        if (features == null) {
            return false;
        }
        for (JSONWriter.Feature feature : features) {
            if (feature == expected) {
                return true;
            }
        }
        return false;
    }

    private static JsonElement toJsonElement(Object value, JsonSerializationContext context) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        if (value instanceof JsonElement element) {
            return element;
        }
        if (value instanceof JSONObject object) {
            JsonObject jsonObject = new JsonObject();
            for (Map.Entry<String, Object> entry : object.entrySet()) {
                jsonObject.add(entry.getKey(), toJsonElement(entry.getValue(), context));
            }
            return jsonObject;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject jsonObject = new JsonObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                jsonObject.add(String.valueOf(entry.getKey()), toJsonElement(entry.getValue(), context));
            }
            return jsonObject;
        }
        if (value instanceof JSONArray array) {
            com.google.gson.JsonArray jsonArray = new com.google.gson.JsonArray();
            for (Object item : array) {
                jsonArray.add(toJsonElement(item, context));
            }
            return jsonArray;
        }
        if (value instanceof Iterable<?> iterable) {
            com.google.gson.JsonArray jsonArray = new com.google.gson.JsonArray();
            for (Object item : iterable) {
                jsonArray.add(toJsonElement(item, context));
            }
            return jsonArray;
        }
        if (value.getClass().isArray()) {
            com.google.gson.JsonArray jsonArray = new com.google.gson.JsonArray();
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                jsonArray.add(toJsonElement(Array.get(value, index), context));
            }
            return jsonArray;
        }
        return context.serialize(value);
    }

    private static JSONObject toJSONObject(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonObject()) {
            throw new JsonParseException("JSON 根节点不是对象");
        }
        return (JSONObject) toJavaValue(element);
    }

    private static JSONArray toJSONArray(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (!element.isJsonArray()) {
            throw new JsonParseException("JSON 根节点不是数组");
        }
        return (JSONArray) toJavaValue(element);
    }

    /** Java Time 类型统一按 ISO-8601 字符串读写，避免 Java 21 模块封装下的反射访问。 */
    private static final class StringValueTypeAdapter<T> extends TypeAdapter<T> {

        private final Function<String, T> parser;
        private final Function<T, String> formatter;

        private StringValueTypeAdapter(Function<String, T> parser, Function<T, String> formatter) {
            this.parser = parser;
            this.formatter = formatter;
        }

        @Override
        public void write(JsonWriter out, T value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.value(formatter.apply(value));
        }

        @Override
        public T read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            try {
                return parser.apply(in.nextString());
            } catch (RuntimeException exception) {
                throw new JsonParseException("时间格式不合法", exception);
            }
        }
    }

    private static String resolveFieldName(Field field) {
        SerializedName serializedName = field.getAnnotation(SerializedName.class);
        if (serializedName != null) {
            return serializedName.value();
        }
        JSONField jsonField = field.getAnnotation(JSONField.class);
        if (jsonField != null && !jsonField.name().isBlank()) {
            return jsonField.name();
        }
        return field.getName();
    }

    private static List<Field> allInstanceFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentType = type;
        while (currentType != null && currentType != Object.class) {
            for (Field field : currentType.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
            currentType = currentType.getSuperclass();
        }
        return fields;
    }

    /**
     * 同时负责 JSONObject 的读写，避免分别注册 serializer/deserializer 时其中一侧
     * 被 Gson 的后注册适配器覆盖。
     */
    private static final class JSONObjectTypeAdapter
            implements JsonSerializer<JSONObject>, JsonDeserializer<JSONObject> {

        @Override
        public JsonElement serialize(JSONObject source, Type typeOfSource, JsonSerializationContext context) {
            return toJsonElement(source, context);
        }

        @Override
        public JSONObject deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return toJSONObject(json);
        }
    }

    /** 同时负责 JSONArray 的读写，并递归保留数组中的动态 JSON 对象类型。 */
    private static final class JSONArrayTypeAdapter
            implements JsonSerializer<JSONArray>, JsonDeserializer<JSONArray> {

        @Override
        public JsonElement serialize(JSONArray source, Type typeOfSource, JsonSerializationContext context) {
            return toJsonElement(source, context);
        }

        @Override
        public JSONArray deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return toJSONArray(json);
        }
    }

    /** 优先解释项目 JSONField 的字段别名，保持现有 JSON 契约不随库迁移改变。 */
    private static final class JSONFieldNamingStrategy implements FieldNamingStrategy {

        @Override
        public String translateName(Field field) {
            return resolveFieldName(field);
        }
    }

    /**
     * 在 Gson 反射适配器输出后，将标记为 WriteLongAsString 的字段改成字符串。
     * 这样既能保留 Gson 对普通对象的默认处理，又能精确覆盖前端 ID 的精度保护。
     */
    private static final class JSONFieldFeatureAdapterFactory implements TypeAdapterFactory {

        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<?> rawType = type.getRawType();
            if (rawType.isPrimitive()
                    || rawType.isEnum()
                    || rawType.isInterface()
                    || rawType.isArray()
                    || Iterable.class.isAssignableFrom(rawType)
                    || Map.class.isAssignableFrom(rawType)) {
                return null;
            }

            List<Field> longFields = allInstanceFields(rawType).stream()
                    .filter(field -> hasWriteLongAsString(field.getAnnotation(JSONField.class)))
                    .toList();
            if (longFields.isEmpty()) {
                return null;
            }

            TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
            TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);
            return new TypeAdapter<>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                        return;
                    }
                    JsonElement tree = delegate.toJsonTree(value);
                    if (tree.isJsonObject()) {
                        JsonObject object = tree.getAsJsonObject();
                        for (Field field : longFields) {
                            String fieldName = resolveFieldName(field);
                            JsonElement fieldValue = object.get(fieldName);
                            if (fieldValue != null && fieldValue.isJsonPrimitive()
                                    && fieldValue.getAsJsonPrimitive().isNumber()) {
                                object.addProperty(fieldName, fieldValue.getAsNumber().toString());
                            }
                        }
                    }
                    elementAdapter.write(out, tree);
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    return delegate.read(in);
                }
            };
        }

        private static boolean hasWriteLongAsString(JSONField field) {
            if (field == null) {
                return false;
            }
            for (JSONWriter.Feature feature : field.serializeFeatures()) {
                if (feature == JSONWriter.Feature.WriteLongAsString) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 严格解析枚举值，避免 Gson 对未知枚举静默返回 null，导致损坏配置继续进入业务流程。
     * 同时支持 JSONField(value = true) 声明的业务编码。
     */
    private static final class StrictEnumTypeAdapterFactory implements TypeAdapterFactory {

        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<?> rawType = type.getRawType();
            if (!rawType.isEnum()) {
                return null;
            }

            Map<String, Object> constantsByLabel = new LinkedHashMap<>();
            Map<Object, String> labelsByConstant = new LinkedHashMap<>();
            Field businessValueField = findBusinessValueField(rawType);
            boolean useBusinessValue = businessValueField != null && businessValueField.trySetAccessible();
            Object[] constants = rawType.getEnumConstants();
            for (Object constant : constants) {
                Enum<?> enumConstant = (Enum<?>) constant;
                String label = enumConstant.name();
                try {
                    Field enumField = rawType.getField(enumConstant.name());
                    SerializedName serializedName = enumField.getAnnotation(SerializedName.class);
                    if (serializedName != null) {
                        label = serializedName.value();
                    }
                    if (useBusinessValue) {
                        Object businessValue = businessValueField.get(constant);
                        label = String.valueOf(businessValue);
                    }
                } catch (ReflectiveOperationException exception) {
                    throw new JSONException("读取枚举 JSON 编码失败: " + rawType.getName(), exception);
                }
                constantsByLabel.putIfAbsent(label, constant);
                constantsByLabel.putIfAbsent(enumConstant.name(), constant);
                labelsByConstant.put(constant, label);
            }

            return new TypeAdapter<>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                        return;
                    }
                    out.value(labelsByConstant.get(value));
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                        in.nextNull();
                        return null;
                    }
                    String label = in.nextString();
                    Object constant = constantsByLabel.get(label);
                    if (constant == null) {
                        throw new JsonParseException("未知枚举值: " + rawType.getName() + "=" + label);
                    }
                    return (T) constant;
                }
            };
        }

        private static Field findBusinessValueField(Class<?> enumType) {
            for (Field field : enumType.getDeclaredFields()) {
                JSONField jsonField = field.getAnnotation(JSONField.class);
                if (jsonField != null && jsonField.value() && !field.isEnumConstant()) {
                    return field;
                }
            }
            return null;
        }
    }

    /** 根据 JSONType 的 typeKey/seeAlso/typeName 完成工作流节点配置多态转换。 */
    private static final class PolymorphicJSONTypeAdapterFactory implements TypeAdapterFactory {

        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<?> rawType = type.getRawType();
            Class<?> polymorphicRoot = findPolymorphicRoot(rawType);
            if (polymorphicRoot == null) {
                return null;
            }
            JSONType typeMetadata = polymorphicRoot.getAnnotation(JSONType.class);

            Map<String, Class<?>> subtypeByLabel = new LinkedHashMap<>();
            Map<Class<?>, String> labelBySubtype = new LinkedHashMap<>();
            Map<Class<?>, TypeAdapter<?>> adapterBySubtype = new LinkedHashMap<>();
            for (Class<?> subtype : typeMetadata.seeAlso()) {
                JSONType subtypeMetadata = subtype.getAnnotation(JSONType.class);
                String label = subtypeMetadata == null || subtypeMetadata.typeName().isBlank()
                        ? subtype.getSimpleName()
                        : subtypeMetadata.typeName();
                if (subtypeByLabel.put(label, subtype) != null) {
                    throw new JSONException("工作流 JSON 类型编码重复: " + label);
                }
                labelBySubtype.put(subtype, label);
                // 跳过当前工厂取得具体类型适配器，避免运行时类型包装再次进入多态工厂。
                adapterBySubtype.put(subtype, gson.getDelegateAdapter(this, TypeToken.get(subtype)));
            }

            TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);
            String typeKey = typeMetadata.typeKey();
            return new TypeAdapter<>() {
                @Override
                public void write(JsonWriter out, T value) throws IOException {
                    if (value == null) {
                        out.nullValue();
                        return;
                    }
                    Class<?> subtype = findSubtype(value.getClass(), labelBySubtype);
                    if (subtype == null) {
                        throw new JsonParseException("未注册的工作流 JSON 子类型: " + value.getClass().getName());
                    }
                    TypeAdapter<Object> subtypeAdapter = adapter(adapterBySubtype.get(subtype));
                    JsonElement tree = subtypeAdapter.toJsonTree(value);
                    if (!tree.isJsonObject()) {
                        throw new JsonParseException("工作流 JSON 子类型必须序列化为对象: " + subtype.getName());
                    }
                    tree.getAsJsonObject().addProperty(typeKey, labelBySubtype.get(subtype));
                    elementAdapter.write(out, tree);
                }

                @Override
                public T read(JsonReader in) throws IOException {
                    JsonElement tree = elementAdapter.read(in);
                    if (tree == null || tree.isJsonNull()) {
                        return null;
                    }
                    if (!tree.isJsonObject()) {
                        throw new JsonParseException("工作流多态 JSON 必须是对象");
                    }
                    JsonElement typeElement = tree.getAsJsonObject().get(typeKey);
                    if (typeElement == null || typeElement.isJsonNull()) {
                        throw new JsonParseException("工作流多态 JSON 缺少类型字段: " + typeKey);
                    }
                    Class<?> subtype = subtypeByLabel.get(typeElement.getAsString());
                    if (subtype == null) {
                        throw new JsonParseException("未知的工作流 JSON 类型: " + typeElement.getAsString());
                    }
                    TypeAdapter<Object> subtypeAdapter = adapter(adapterBySubtype.get(subtype));
                    return (T) subtypeAdapter.fromJsonTree(tree);
                }
            };
        }

        private static Class<?> findSubtype(Class<?> actualType, Map<Class<?>, String> labelBySubtype) {
            for (Class<?> subtype : labelBySubtype.keySet()) {
                if (subtype.isAssignableFrom(actualType)) {
                    return subtype;
                }
            }
            return null;
        }

        /**
         * Gson 序列化接口字段时会优先选择运行时具体类型适配器，因此具体实现也必须能回溯到
         * 声明 seeAlso 的多态根；否则写出时不会附加类型字段，读入接口时必然失败。
         */
        private static Class<?> findPolymorphicRoot(Class<?> rawType) {
            JSONType ownMetadata = rawType.getAnnotation(JSONType.class);
            if (ownMetadata != null && ownMetadata.seeAlso().length > 0) {
                return rawType;
            }
            for (Class<?> interfaceType : rawType.getInterfaces()) {
                Class<?> root = findPolymorphicRoot(interfaceType);
                if (root != null) {
                    return root;
                }
            }
            Class<?> superType = rawType.getSuperclass();
            return superType == null || superType == Object.class ? null : findPolymorphicRoot(superType);
        }

        @SuppressWarnings("unchecked")
        private static TypeAdapter<Object> adapter(TypeAdapter<?> adapter) {
            return (TypeAdapter<Object>) adapter;
        }
    }
}
