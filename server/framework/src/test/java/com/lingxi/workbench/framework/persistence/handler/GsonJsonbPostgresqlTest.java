package com.lingxi.workbench.framework.persistence.handler;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GsonJsonbPostgresqlTest {

    @Test
    void shouldInsertQueryAndUpdateStrongTypedJsonbFields() throws Exception {
        Connection connection = openDevelopmentDatabase();
        if (connection == null) {
            return;
        }

        try (connection) {
            connection.setAutoCommit(false);
            createTemporaryTable(connection);

            GsonJsonbTypeHandler objectHandler = handlerFor("config");
            GsonJsonbTypeHandler listHandler = handlerFor("configs");
            GsonJsonbTypeHandler mapHandler = handlerFor("settings");
            insertFixture(connection, objectHandler, listHandler, mapHandler);
            assertFixture(connection, objectHandler, listHandler, mapHandler);
            updateFixture(connection, listHandler);
            assertUpdatedFixture(connection, listHandler);
            connection.rollback();
        }
    }

    private static Connection openDevelopmentDatabase() {
        String url = System.getenv().getOrDefault(
                "LINGXI_DB_URL", "jdbc:postgresql://localhost:5432/lingxi_dev");
        String username = System.getenv().getOrDefault("LINGXI_DB_USERNAME", "postgres");
        String password = System.getenv().getOrDefault("LINGXI_DB_PASSWORD", "postgres");
        try {
            DriverManager.setLoginTimeout(2);
            return DriverManager.getConnection(url, username, password);
        } catch (SQLException exception) {
            Assumptions.assumeTrue(false, "本地 PostgreSQL 不可用，跳过 JSONB 集成测试");
            return null;
        }
    }

    private static void createTemporaryTable(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                CREATE TEMPORARY TABLE jsonb_type_handler_test
                (
                    id       BIGINT PRIMARY KEY,
                    config   JSONB,
                    configs  JSONB,
                    settings JSONB
                )
                """)) {
            statement.execute();
        }
    }

    private static void insertFixture(
            Connection connection,
            GsonJsonbTypeHandler objectHandler,
            GsonJsonbTypeHandler listHandler,
            GsonJsonbTypeHandler mapHandler) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO jsonb_type_handler_test VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, 1L);
            objectHandler.setParameter(
                    statement, 2, new SampleConfig("chat", true), JdbcType.OTHER);
            listHandler.setParameter(
                    statement, 3, List.of(new SampleConfig("embedding", false)), JdbcType.OTHER);
            mapHandler.setParameter(
                    statement, 4, Map.of("models", List.of("chat", "embedding")), JdbcType.OTHER);
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO jsonb_type_handler_test VALUES (?, ?, ?, ?)")) {
            statement.setLong(1, 2L);
            objectHandler.setParameter(statement, 2, null, JdbcType.OTHER);
            listHandler.setParameter(statement, 3, List.of(), JdbcType.OTHER);
            mapHandler.setParameter(statement, 4, Map.of(), JdbcType.OTHER);
            statement.executeUpdate();
        }
    }

    private static void assertFixture(
            Connection connection,
            GsonJsonbTypeHandler objectHandler,
            GsonJsonbTypeHandler listHandler,
            GsonJsonbTypeHandler mapHandler) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT config, configs, settings FROM jsonb_type_handler_test WHERE id = 1");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            assertEquals(
                    new SampleConfig("chat", true), objectHandler.getResult(resultSet, "config"));
            assertEquals(
                    List.of(new SampleConfig("embedding", false)),
                    listHandler.getResult(resultSet, "configs"));
            assertEquals(
                    Map.of("models", List.of("chat", "embedding")),
                    mapHandler.getResult(resultSet, "settings"));
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT config, configs, settings FROM jsonb_type_handler_test WHERE id = 2");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            assertNull(objectHandler.getResult(resultSet, "config"));
            assertEquals(List.of(), listHandler.getResult(resultSet, "configs"));
            assertEquals(Map.of(), mapHandler.getResult(resultSet, "settings"));
        }
    }

    private static void updateFixture(
            Connection connection, GsonJsonbTypeHandler listHandler) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE jsonb_type_handler_test SET configs = ? WHERE id = 1")) {
            listHandler.setParameter(
                    statement, 1, List.of(new SampleConfig("rerank", true)), JdbcType.OTHER);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void assertUpdatedFixture(
            Connection connection, GsonJsonbTypeHandler listHandler) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT configs FROM jsonb_type_handler_test WHERE id = 1");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            assertEquals(
                    List.of(new SampleConfig("rerank", true)),
                    listHandler.getResult(resultSet, "configs"));
        }
    }

    private static GsonJsonbTypeHandler handlerFor(String fieldName) throws Exception {
        Field field = JsonFields.class.getDeclaredField(fieldName);
        return new GsonJsonbTypeHandler(field.getType(), field);
    }

    private record SampleConfig(String name, boolean enabled) {
    }

    @SuppressWarnings("unused")
    private static final class JsonFields {
        private SampleConfig config;
        private List<SampleConfig> configs;
        private Map<String, Object> settings;
    }
}
