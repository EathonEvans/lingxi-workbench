package com.lingxi.workbench.admin.tool.service.impl;

import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.admin.tool.common.enums.ToolParameterDataType;
import com.lingxi.workbench.admin.tool.model.config.ToolParameterDefinition;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolParameterSchemaFactoryTest {

    private final ToolParameterSchemaFactory factory = new ToolParameterSchemaFactory();

    @Test
    void shouldBuildSchemaAndRestoreParameterDefinitions() {
        List<ToolParameterDefinition> parameters = List.of(
                parameter("city", "城市名称", ToolParameterDataType.STRING, true),
                parameter("days", "查询天数", ToolParameterDataType.INTEGER, false));

        JSONObject schema = factory.build(parameters);
        List<ToolParameterDefinition> restored = factory.parse(schema);

        assertEquals("object", schema.getString("type"));
        assertEquals("string", schema.getJSONObject("properties").getJSONObject("city").getString("type"));
        assertTrue(schema.getJSONArray("required").contains("city"));
        assertEquals(2, restored.size());
        assertTrue(restored.getFirst().getRequired());
        assertFalse(restored.get(1).getRequired());
    }

    @Test
    void shouldBuildEmptyObjectSchemaWhenToolHasNoParameters() {
        JSONObject schema = factory.build(List.of());

        assertTrue(schema.getJSONObject("properties").isEmpty());
        assertFalse(schema.containsKey("required"));
        assertFalse(schema.getBooleanValue("additionalProperties"));
    }

    @Test
    void shouldRejectDuplicatedParameterNames() {
        List<ToolParameterDefinition> parameters = List.of(
                parameter("city", "城市名称", ToolParameterDataType.STRING, true),
                parameter("city", "备用城市名称", ToolParameterDataType.STRING, false));

        BusinessException exception = assertThrows(BusinessException.class, () -> factory.build(parameters));

        assertEquals("参数字段名不能重复：city", exception.getMessage());
    }

    @Test
    void shouldRejectMoreThanFiveParameters() {
        List<ToolParameterDefinition> parameters = List.of(
                parameter("p1", "参数 1", ToolParameterDataType.STRING, false),
                parameter("p2", "参数 2", ToolParameterDataType.STRING, false),
                parameter("p3", "参数 3", ToolParameterDataType.STRING, false),
                parameter("p4", "参数 4", ToolParameterDataType.STRING, false),
                parameter("p5", "参数 5", ToolParameterDataType.STRING, false),
                parameter("p6", "参数 6", ToolParameterDataType.STRING, false));

        BusinessException exception = assertThrows(BusinessException.class, () -> factory.build(parameters));

        assertEquals("一个 Tool 最多配置 5 个参数", exception.getMessage());
    }

    @Test
    void shouldParseMapBackedSchemaDiscoveredFromMcpSdk() {
        JSONObject schema = new JSONObject(Map.of(
                "type", "object",
                "properties", Map.of("city", Map.of("type", "string", "description", "目标城市")),
                "required", List.of("city")));

        List<ToolParameterDefinition> parameters = factory.parse(schema);

        assertEquals(1, parameters.size());
        assertEquals("city", parameters.getFirst().getName());
        assertTrue(parameters.getFirst().getRequired());
    }

    private static ToolParameterDefinition parameter(String name, String description, ToolParameterDataType dataType, boolean required) {
        return ToolParameterDefinition.builder()
                .name(name)
                .description(description)
                .dataType(dataType)
                .required(required)
                .build();
    }
}
