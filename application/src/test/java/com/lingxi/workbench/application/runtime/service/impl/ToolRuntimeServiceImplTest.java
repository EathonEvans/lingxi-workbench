package com.lingxi.workbench.application.runtime.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeToolEntity;
import com.lingxi.workbench.application.runtime.repo.ToolRuntimeRepo;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.AgentToolRegistry;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRuntimeServiceImplTest {

    @Test
    void shouldUseManagedDefinitionAndDeployedExecutor() {
        RuntimeToolEntity managedDefinition = activeDefinition();
        ToolRuntimeServiceImpl service = new ToolRuntimeServiceImpl(new StubToolRuntimeRepo(List.of(managedDefinition)), new AgentToolRegistry(List.of(new StubAgentTool())));

        AgentTool resolved = service.resolveActiveTools(List.of("weather_query")).getFirst();
        JSONObject runtimeSchema = JSONObject.parseObject(resolved.callback().getToolDefinition().inputSchema());

        assertEquals("天气查询", resolved.definition().getDisplayName());
        assertEquals("查询指定城市天气", resolved.callback().getToolDefinition().description());
        assertEquals("string", runtimeSchema.getJSONObject("properties").getJSONObject("city").getString("type"));
        assertEquals("目标城市", runtimeSchema.getJSONObject("properties").getJSONObject("city").getString("description"));
        assertTrue(runtimeSchema.getJSONArray("required").contains("city"));
        assertFalse(runtimeSchema.getBooleanValue("additionalProperties"));
        assertEquals("executed:{\"city\":\"上海\"}", resolved.callback().call("{\"city\":\"上海\"}"));
    }

    @Test
    void shouldRejectToolWithoutActiveManagedDefinition() {
        ToolRuntimeServiceImpl service = new ToolRuntimeServiceImpl(new StubToolRuntimeRepo(List.of()), new AgentToolRegistry(List.of(new StubAgentTool())));

        AgentToolException exception = assertThrows(AgentToolException.class, () -> service.resolveActiveTools(List.of("weather_query")));

        assertEquals("Tool不存在、已停用或来源类型不受支持：weather_query", exception.getMessage());
    }

    private static RuntimeToolEntity activeDefinition() {
        RuntimeToolEntity entity = new RuntimeToolEntity();
        entity.setCode("weather_query");
        entity.setName("天气查询");
        entity.setDescription("查询指定城市天气");
        entity.setSourceType("LOCAL_FUNCTION");
        entity.setStatus("ACTIVE");
        entity.setParameterSchema(JSONObject.of(
                "type", "object",
                "properties", JSONObject.of(
                        "city", JSONObject.of(
                                "type", "string",
                                "description", "目标城市")),
                "required", List.of("city"),
                "additionalProperties", false));
        return entity;
    }

    private static final class StubToolRuntimeRepo extends ToolRuntimeRepo {

        private final List<RuntimeToolEntity> definitions;

        private StubToolRuntimeRepo(List<RuntimeToolEntity> definitions) {
            super(null);
            this.definitions = definitions;
        }

        @Override
        public List<RuntimeToolEntity> findActiveByCodes(List<String> codes) {
            return definitions;
        }
    }

    private static final class StubAgentTool implements AgentTool {

        private final ToolCallback callback = new StubToolCallback();

        @Override
        public AgentToolDefinition definition() {
            return AgentToolDefinition.builder()
                    .code("weather_query")
                    .displayName("代码内旧名称")
                    .description("代码内旧描述")
                    .build();
        }

        @Override
        public ToolCallback callback() {
            return callback;
        }
    }

    private static final class StubToolCallback implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name("weather_query")
                    .description("代码内旧描述")
                    .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                    .build();
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return ToolMetadata.builder().build();
        }

        @Override
        public String call(String toolInput) {
            return "executed:" + toolInput;
        }
    }
}
