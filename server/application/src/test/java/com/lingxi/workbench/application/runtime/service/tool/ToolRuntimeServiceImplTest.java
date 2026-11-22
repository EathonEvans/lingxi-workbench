package com.lingxi.workbench.application.runtime.service.tool;

import com.lingxi.workbench.framework.json.JSONObject;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeToolEntity;
import com.lingxi.workbench.application.runtime.common.enums.RuntimeToolSourceType;
import com.lingxi.workbench.application.runtime.common.enums.RuntimeMcpAuthenticationType;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeMcpServerEntity;
import com.lingxi.workbench.application.runtime.repo.McpServerRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.ToolRuntimeRepo;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.AgentToolRegistry;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolRuntimeServiceImplTest {

    @Test
    void shouldUseManagedDefinitionAndDeployedExecutor() {
        RuntimeToolEntity managedDefinition = activeDefinition();
        ToolRuntimeServiceImpl service = new ToolRuntimeServiceImpl(new StubToolRuntimeRepo(List.of(managedDefinition)), new StubMcpServerRuntimeRepo(), new AgentToolRegistry(List.of(new StubAgentTool())), null, null);

        AgentTool resolved = service.resolveActiveTools(List.of("weather_query")).getFirst();
        JSONObject runtimeSchema = new JSONObject(resolved.inputSchema());

        assertEquals("天气查询", resolved.definition().getDisplayName());
        assertEquals("查询指定城市天气", resolved.definition().getDescription());
        assertEquals("string", runtimeSchema.getJSONObject("properties").getJSONObject("city").getString("type"));
        assertEquals("目标城市", runtimeSchema.getJSONObject("properties").getJSONObject("city").getString("description"));
        assertTrue(runtimeSchema.getJSONArray("required").contains("city"));
        assertFalse(runtimeSchema.getBooleanValue("additionalProperties"));
        assertEquals("executed:{\"city\":\"上海\"}", resolved.execute(request("{\"city\":\"上海\"}")));
    }

    @Test
    void shouldRejectToolWithoutActiveManagedDefinition() {
        ToolRuntimeServiceImpl service = new ToolRuntimeServiceImpl(new StubToolRuntimeRepo(List.of()), new StubMcpServerRuntimeRepo(), new AgentToolRegistry(List.of(new StubAgentTool())), null, null);

        AgentToolException exception = assertThrows(AgentToolException.class, () -> service.resolveActiveTools(List.of("weather_query")));

        assertEquals("Tool不存在、已停用或来源类型不受支持：weather_query", exception.getMessage());
    }

    @Test
    void shouldResolveMcpToolWithoutLocalJavaExecutor() {
        RuntimeToolEntity definition = activeMcpDefinition();
        ToolRuntimeServiceImpl service = new ToolRuntimeServiceImpl(new StubToolRuntimeRepo(List.of(definition)), new StubMcpServerRuntimeRepo(List.of(activeMcpServer())), new AgentToolRegistry(List.of()), null, null);

        AgentTool resolved = service.resolveActiveTools(List.of("local_mcp_calculator")).getFirst();

        assertEquals("local_mcp_calculator", resolved.definition().getCode());
        assertEquals("远端基础计算器", resolved.definition().getDescription());
        assertTrue(((Map<?, ?>) resolved.inputSchema().get("properties")).containsKey("leftOperand"));
    }

    @Test
    void shouldRejectMcpToolWhenServerIsNotRuntimeAvailable() {
        RuntimeToolEntity definition = activeMcpDefinition();
        ToolRuntimeServiceImpl service = new ToolRuntimeServiceImpl(new StubToolRuntimeRepo(List.of(definition)), new StubMcpServerRuntimeRepo(List.of()), new AgentToolRegistry(List.of()), null, null);

        AgentToolException exception = assertThrows(AgentToolException.class, () -> service.resolveActiveTools(List.of("local_mcp_calculator")));

        assertEquals("MCP Server不存在、未启用或最近连接测试未成功：tool=local_mcp_calculator", exception.getMessage());
    }

    private static RuntimeToolEntity activeDefinition() {
        RuntimeToolEntity entity = new RuntimeToolEntity();
        entity.setCode("weather_query");
        entity.setName("天气查询");
        entity.setDescription("查询指定城市天气");
        entity.setSourceType(RuntimeToolSourceType.LOCAL_FUNCTION);
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

    private static RuntimeToolEntity activeMcpDefinition() {
        RuntimeToolEntity entity = new RuntimeToolEntity();
        entity.setCode("local_mcp_calculator");
        entity.setName("MCP 计算器");
        entity.setDescription("远端基础计算器");
        entity.setSourceType(RuntimeToolSourceType.MCP);
        entity.setMcpServerId(20L);
        entity.setRemoteToolName("system_calculator");
        entity.setStatus("ACTIVE");
        entity.setParameterSchema(JSONObject.of(
                "type", "object",
                "properties", JSONObject.of(
                        "leftOperand", JSONObject.of("type", "number"),
                        "operator", JSONObject.of("type", "string"),
                        "rightOperand", JSONObject.of("type", "number"))));
        return entity;
    }

    private static RuntimeMcpServerEntity activeMcpServer() {
        RuntimeMcpServerEntity entity = new RuntimeMcpServerEntity();
        entity.setId(20L);
        entity.setCode("local_mcp");
        entity.setTransportType("STREAMABLE_HTTP");
        entity.setBaseUrl("http://127.0.0.1:8091");
        entity.setEndpoint("/mcp");
        entity.setAuthenticationType(RuntimeMcpAuthenticationType.NONE);
        entity.setRequestTimeoutSeconds(5);
        entity.setStatus("ACTIVE");
        entity.setLastTestStatus("SUCCESS");
        return entity;
    }

    private static final class StubMcpServerRuntimeRepo extends McpServerRuntimeRepo {

        private final List<RuntimeMcpServerEntity> servers;

        private StubMcpServerRuntimeRepo() {
            this(List.of());
        }

        private StubMcpServerRuntimeRepo(List<RuntimeMcpServerEntity> servers) {
            super(null);
            this.servers = servers;
        }

        @Override
        public List<RuntimeMcpServerEntity> findRuntimeAvailableByIds(List<Long> ids) {
            return servers;
        }
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

        @Override
        public AgentToolDefinition definition() {
            return AgentToolDefinition.builder()
                    .code("weather_query")
                    .displayName("代码内旧名称")
                    .description("代码内旧描述")
                    .build();
        }

        @Override
        public Map<String, Object> inputSchema() {
            return Map.of("type", "object", "properties", Map.of());
        }

        @Override
        public String execute(AgentToolRequest request) {
            return "executed:" + request.input();
        }
    }

    private static AgentToolRequest request(String input) {
        return new AgentToolRequest(input, AgentToolExecutionContext.builder().executionId("test").build(), null);
    }
}
