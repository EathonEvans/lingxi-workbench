package com.lingxi.workbench.infra.ai.tool.builtin;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemCurrentTimeAgentToolTest {

    @Test
    void shouldKeepDefinitionCallbackAndExecutionInOneTool() {
        SystemCurrentTimeAgentTool tool = new SystemCurrentTimeAgentTool();
        ToolCallback callback = tool.callback();

        String inputSchema = callback.getToolDefinition().inputSchema();
        String result = callback.call("{\"zoneId\":\"Asia/Shanghai\"}");

        assertEquals(tool.definition().getCode(), callback.getToolDefinition().name());
        assertEquals("查询当前时间", tool.definition().getDisplayName());
        assertTrue(inputSchema.contains("\"zoneId\""));
        assertTrue(result.contains("\"zoneId\":\"Asia/Shanghai\""));
        assertTrue(result.contains("\"currentTime\""));
        assertTrue(result.contains("\"currentDate\""));
    }
}
