package com.lingxi.workbench.framework.persistence.handler;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.postgresql.util.PGobject;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * PostgreSQL JSONB 与 Java 强类型对象之间的通用类型处理器。
 *
 * <p>MyBatis-Plus 3.5.6 及以上版本会优先调用包含 {@link Field} 的构造方法，
 * 因此 {@code List<T>}、{@code Map<String, Object>} 等字段能够保留完整泛型信息。
 */
@MappedJdbcTypes(JdbcType.OTHER)
public class Fastjson2JsonbTypeHandler extends AbstractJsonTypeHandler<Object> {

    public Fastjson2JsonbTypeHandler(Class<?> type) {
        super(type);
    }

    public Fastjson2JsonbTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Object parameter, JdbcType jdbcType) throws SQLException {
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        jsonb.setValue(JSON.toJSONString(parameter));
        statement.setObject(index, jsonb);
    }

    @Override
    public Object parse(String json) {
        return JSON.parseObject(json, getFieldType());
    }

    @Override
    public String toJson(Object value) {
        return JSON.toJSONString(value);
    }
}
