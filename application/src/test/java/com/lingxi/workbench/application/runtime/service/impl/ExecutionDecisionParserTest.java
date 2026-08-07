package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionDecisionAction;
import com.lingxi.workbench.application.runtime.common.enums.StepInputSource;
import com.lingxi.workbench.application.runtime.model.ExecutionDecision;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionDecisionParserTest {

    private final ExecutionDecisionParser parser = new ExecutionDecisionParser();

    @Test
    void shouldParseStrictPlanDecision() {
        String output = """
                {
                  "action": "EXECUTE_PLAN",
                  "plan": {
                    "title": "处理申请",
                    "steps": [{
                      "id": "collect",
                      "name": "收集信息",
                      "capabilityCode": "support_agent",
                      "dependsOn": [],
                      "inputs": [{"name": "query", "source": "USER_QUERY"}]
                    }]
                  }
                }
                """;

        ExecutionDecision decision = parser.parse(output);

        assertEquals(ExecutionDecisionAction.EXECUTE_PLAN, decision.getAction());
        assertEquals("collect", decision.getPlan().steps().getFirst().getId());
        assertEquals(StepInputSource.USER_QUERY, decision.getPlan().steps().getFirst().inputs().getFirst().getSource());
    }

    @Test
    void shouldRejectMarkdownOrUnknownFields() {
        assertThrows(BusinessException.class, () -> parser.parse("```json\n{\"action\":\"CLARIFY\"}\n```"));
        assertThrows(BusinessException.class, () -> parser.parse("{\"action\":\"CLARIFY\",\"reasoning\":\"hidden\"}"));
        assertThrows(BusinessException.class, () -> parser.parse("{\"action\":\"EXECUTE_PLAN\",\"plan\":{\"title\":\"x\",\"steps\":[{\"id\":\"s1\",\"unknown\":true}]}}"));
    }
}
