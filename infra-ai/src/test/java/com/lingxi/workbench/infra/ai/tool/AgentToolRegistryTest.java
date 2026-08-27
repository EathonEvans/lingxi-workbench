package com.lingxi.workbench.infra.ai.tool;

import com.lingxi.workbench.infra.ai.tool.builtin.SystemCurrentTimeAgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentToolRegistryTest {

    @Test
    void shouldExposeDefinitionAndResolveTheSameTool() {
        SystemCurrentTimeAgentTool tool = new SystemCurrentTimeAgentTool();
        AgentToolRegistry registry = new AgentToolRegistry(List.of(tool));

        assertEquals(tool.definition(), registry.definitions().getFirst());
        assertEquals(tool, registry.resolve(List.of(tool.definition().getCode())).getFirst());
    }

    @Test
    void shouldRejectDuplicatedToolCodesAtStartup() {
        AgentTool first = tool("duplicated_tool", "工具一");
        AgentTool second = tool("duplicated_tool", "工具二");

        assertThrows(IllegalStateException.class, () -> new AgentToolRegistry(List.of(first, second)));
    }

    private static AgentTool tool(String code, String displayName) {
        AgentToolDefinition definition = AgentToolDefinition.builder().code(code).displayName(displayName).description("测试工具").build();
        return new AgentTool() {
            @Override
            public AgentToolDefinition definition() {
                return definition;
            }

            @Override
            public Map<String, Object> inputSchema() {
                return Map.of("type", "object", "properties", Map.of());
            }

            @Override
            public String execute(AgentToolRequest request) {
                return request.input();
            }
        };
    }
}
