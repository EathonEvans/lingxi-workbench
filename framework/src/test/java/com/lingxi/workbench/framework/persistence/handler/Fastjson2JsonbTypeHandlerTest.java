package com.lingxi.workbench.framework.persistence.handler;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.type.TypeHandler;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class Fastjson2JsonbTypeHandlerTest {

    @Test
    void shouldDeserializePojoWithDeclaredType() throws Exception {
        Fastjson2JsonbTypeHandler handler = handlerFor("config");

        Object result = handler.getNullableResult(
                resultSetReturning("{\"name\":\"chat\",\"enabled\":true}"), "value");

        assertEquals(new SampleConfig("chat", true), result);
    }

    @Test
    void shouldPreserveListElementGenericType() throws Exception {
        Fastjson2JsonbTypeHandler handler = handlerFor("configs");

        Object result = handler.getNullableResult(
                resultSetReturning("[{\"name\":\"chat\",\"enabled\":true}]"), "value");

        List<?> values = assertInstanceOf(List.class, result);
        assertEquals(new SampleConfig("chat", true), values.getFirst());
    }

    @Test
    void shouldDeserializeMapAndNestedList() throws Exception {
        Fastjson2JsonbTypeHandler handler = handlerFor("settings");

        Object result = handler.getNullableResult(
                resultSetReturning("{\"models\":[\"chat\",\"embedding\"]}"), "value");

        Map<?, ?> values = assertInstanceOf(Map.class, result);
        assertEquals(List.of("chat", "embedding"), values.get("models"));
    }

    @Test
    void shouldHandleNullAndEmptyCollections() throws Exception {
        Fastjson2JsonbTypeHandler listHandler = handlerFor("configs");
        Fastjson2JsonbTypeHandler mapHandler = handlerFor("settings");

        assertNull(listHandler.getNullableResult(resultSetReturning(null), "value"));
        assertNull(listHandler.getNullableResult(resultSetReturning("  "), "value"));
        assertEquals(List.of(),
                listHandler.getNullableResult(resultSetReturning("[]"), "value"));
        assertEquals(Map.of(),
                mapHandler.getNullableResult(resultSetReturning("{}"), "value"));
    }

    @Test
    void shouldBindParameterAsPostgresqlJsonbObject() throws Exception {
        Fastjson2JsonbTypeHandler handler = handlerFor("configs");
        AtomicReference<Object> boundValue = new AtomicReference<>();
        PreparedStatement statement = preparedStatementCapturing(boundValue);

        handler.setNonNullParameter(
                statement,
                1,
                List.of(new SampleConfig("chat", true)),
                JdbcType.OTHER);

        PGobject jsonb = assertInstanceOf(PGobject.class, boundValue.get());
        assertEquals("jsonb", jsonb.getType());
        assertEquals(
                List.of(new SampleConfig("chat", true)),
                JSON.parseArray(jsonb.getValue(), SampleConfig.class));
    }

    @Test
    void shouldReceiveFieldGenericTypeFromMybatisPlusResultMap() throws Exception {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(configuration, "jsonb-test");
        assistant.setCurrentNamespace(JsonEntity.class.getName());
        TableInfo tableInfo = TableInfoHelper.initTableInfo(assistant, JsonEntity.class);
        ResultMap resultMap = configuration.getResultMap(tableInfo.getResultMap());
        ResultMapping mapping = resultMap.getResultMappings().stream()
                .filter(item -> item.getProperty().equals("configs"))
                .findFirst()
                .orElseThrow();
        TypeHandler<?> typeHandler = mapping.getTypeHandler();

        Object result = ((Fastjson2JsonbTypeHandler) typeHandler).getNullableResult(
                resultSetReturning("[{\"name\":\"chat\",\"enabled\":true}]"), "configs");

        List<?> values = assertInstanceOf(List.class, result);
        assertEquals(SampleConfig.class, values.getFirst().getClass());
        assertEquals(new SampleConfig("chat", true), values.getFirst());
    }

    private static Fastjson2JsonbTypeHandler handlerFor(String fieldName) throws Exception {
        Field field = JsonFields.class.getDeclaredField(fieldName);
        return new Fastjson2JsonbTypeHandler(field.getType(), field);
    }

    private static ResultSet resultSetReturning(String json) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getString" -> json;
                    case "wasNull" -> json == null;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static PreparedStatement preparedStatementCapturing(
            AtomicReference<Object> boundValue) {
        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("setObject")) {
                        boundValue.set(arguments[1]);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return 0;
    }

    private record SampleConfig(String name, boolean enabled) {
    }

    @SuppressWarnings("unused")
    private static final class JsonFields {
        private SampleConfig config;
        private List<SampleConfig> configs;
        private Map<String, Object> settings;
    }

    @TableName(value = "json_entity", autoResultMap = true)
    private static final class JsonEntity {
        private Long id;
        @TableField(typeHandler = Fastjson2JsonbTypeHandler.class, jdbcType = JdbcType.OTHER)
        private List<SampleConfig> configs;
    }
}
