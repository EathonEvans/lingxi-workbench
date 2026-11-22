package com.lingxi.workbench.framework.persistence.handler;

import com.lingxi.workbench.framework.json.JSON;
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
public class GsonJsonbTypeHandler extends AbstractJsonTypeHandler<Object> {

    /**
     * 构造类型处理器，用于映射实体中明确类型的字段。
     *
     * @param type 目标 Java 类型
     */
    public GsonJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    /**
     * 构造类型处理器，用于保留泛型字段的完整类型信息。
     *
     * @param type  目标 Java 类型
     * @param field 实体字段元数据，用于获取泛型信息
     */
    public GsonJsonbTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    /**
     * 将非空 Java 对象序列化为 PostgreSQL JSONB 并设置到 PreparedStatement。
     *
     * @param statement 预编译语句
     * @param index     参数位置
     * @param parameter 待序列化的 Java 对象
     * @param jdbcType  JDBC 类型（未使用）
     */
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, Object parameter, JdbcType jdbcType) throws SQLException {
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        jsonb.setValue(JSON.toJSONString(parameter));
        statement.setObject(index, jsonb);
    }

    /**
     * 从数据库 JSONB 字符串反序列化为目标类型的 Java 对象。
     *
     * @param json 数据库中的 JSON 字符串
     * @return 反序列化后的目标类型对象
     */
    @Override
    public Object parse(String json) {
        return JSON.parseObject(json, getFieldType());
    }

    /**
     * 将 Java 对象序列化为 JSON 字符串。
     *
     * @param value 待序列化的对象
     * @return JSON 字符串
     */
    @Override
    public String toJson(Object value) {
        return JSON.toJSONString(value);
    }
}
