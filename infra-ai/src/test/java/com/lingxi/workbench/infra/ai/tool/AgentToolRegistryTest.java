package com.lingxi.workbench.infra.ai.tool;

import com.lingxi.workbench.infra.ai.tool.builtin.SystemCurrentTimeAgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;

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
        ToolCallback callback = FunctionToolCallback.builder(code, (String input) -> input).description("测试工具").inputType(String.class).build();
        return new AgentTool() {
            @Override
            public AgentToolDefinition definition() {
                return definition;
            }

            @Override
            public ToolCallback callback() {
                return callback;
            }
        };
    }
}
