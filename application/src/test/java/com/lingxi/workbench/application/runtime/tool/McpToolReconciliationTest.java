package com.lingxi.workbench.application.runtime.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.infra.ai.tool.AgentToolException;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolReconciliationResult;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolReconciliationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McpToolReconciliationTest {

    @Test
    void shouldParseConfirmedExternalResult() {
        JSONArray contents = JSONArray.of(JSONObject.of("type", "text", "text", JSON.toJSONString(
                JSONObject.of("status", "SUCCEEDED", "result", "receipt-1"))));

        AgentToolReconciliationResult result = McpToolReconciliation.parseResponse(JSON.toJSONString(contents));

        assertEquals(AgentToolReconciliationStatus.SUCCEEDED, result.status());
        assertEquals("receipt-1", result.result());
    }

    @Test
    void shouldRejectUnstructuredRemoteResponse() {
        assertThrows(AgentToolException.class, () -> McpToolReconciliation.parseResponse("[]"));
    }
}
