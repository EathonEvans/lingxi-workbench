package com.lingxi.workbench.admin.common.persistence;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/** 将 Fastjson2 生成的 JSON 字符串安全绑定到 PostgreSQL JSONB 字段。 */
@MappedJdbcTypes(JdbcType.OTHER)
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(
            PreparedStatement statement, int index, String parameter, JdbcType jdbcType)
            throws SQLException {
        // Types.OTHER 让 PostgreSQL 按目标列类型解析参数，避免按 VARCHAR 绑定后拒绝写入 JSONB。
        statement.setObject(index, parameter, Types.OTHER);
    }

    @Override
    public String getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        return resultSet.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        return resultSet.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement statement, int columnIndex)
            throws SQLException {
        return statement.getString(columnIndex);
    }
}
