package com.lingxi.workbench.infra.ai.tool.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentToolDefinitionTest {

    @Test
    void shouldLimitEveryToolToTwoCallsByDefault() {
        AgentToolDefinition definition = definition(0, 0);

        assertEquals(2, definition.effectiveMaxCallsPerRound());
        assertEquals(2, definition.effectiveMaxCallsPerExecution());
    }

    @Test
    void shouldAllowStricterLimitButRejectRelaxingPlatformBoundary() {
        AgentToolDefinition stricter = definition(1, 1);
        AgentToolDefinition relaxed = definition(5, 8);

        assertEquals(1, stricter.effectiveMaxCallsPerRound());
        assertEquals(1, stricter.effectiveMaxCallsPerExecution());
        assertEquals(2, relaxed.effectiveMaxCallsPerRound());
        assertEquals(2, relaxed.effectiveMaxCallsPerExecution());
    }

    private static AgentToolDefinition definition(int maxCallsPerRound, int maxCallsPerExecution) {
        return AgentToolDefinition.builder()
                .code("test_tool")
                .displayName("测试工具")
                .description("验证平台统一工具调用边界")
                .maxCallsPerRound(maxCallsPerRound)
                .maxCallsPerExecution(maxCallsPerExecution)
                .build();
    }
}
