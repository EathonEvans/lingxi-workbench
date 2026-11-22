package com.lingxi.workbench.infra.ai.tool.builtin;

import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionContext;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemCurrentTimeAgentToolTest {

    @Test
    void shouldKeepDefinitionSchemaAndExecutionInOneTool() {
        SystemCurrentTimeAgentTool tool = new SystemCurrentTimeAgentTool();

        String result = tool.execute(new AgentToolRequest("{\"zoneId\":\"Asia/Shanghai\"}",
                AgentToolExecutionContext.builder().executionId("test").build(), null));

        assertEquals("system_current_time", tool.definition().getCode());
        assertEquals("查询当前时间", tool.definition().getDisplayName());
        assertTrue(((java.util.Map<?, ?>) tool.inputSchema().get("properties")).containsKey("zoneId"));
        assertTrue(result.contains("\"zoneId\":\"Asia/Shanghai\""));
        assertTrue(result.contains("\"currentTime\""));
        assertTrue(result.contains("\"currentDate\""));
    }
}
