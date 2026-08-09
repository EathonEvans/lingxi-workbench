package com.lingxi.workbench.application.runtime.model;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentRuntimeDefinitionJsonTest {

    @Test
    void shouldReadPublishedAgentVersionContract() {
        AgentRuntimeDefinition definition = JSON.parseObject(
                """
                        {
                          "schemaVersion": 4,
                          "agentType": "REACT",
                          "chatModelInstanceId": 10,
                          "systemPromptVersionId": 20,
                          "userPromptVersionId": 21,
                          "toolCodes": ["system_current_time"],
                          "toolPolicy": {
                            "maxToolCalls": 2,
                            "maxResultCharacters": 8000
                          },
                          "knowledgeSearch": {
                            "knowledgeBaseId": 30,
                            "knowledgeIndexVersionId": 31,
                            "embeddingModelInstanceId": 40,
                            "topK": 5,
                            "similarityThreshold": 0.65,
                            "maxResultTokens": 2000,
                            "maxQueryCharacters": 1000,
                            "timeoutSeconds": 8
                          }
                        }
                        """,
                AgentRuntimeDefinition.class);

        AgentRuntimeDefinition.KnowledgeSearchCapability capability = definition.getKnowledgeSearch();
        assertEquals(AgentType.REACT, definition.getAgentType());
        assertEquals(20L, definition.getSystemPromptVersionId());
        assertEquals(21L, definition.getUserPromptVersionId());
        assertEquals("system_current_time", definition.resolveToolCodes().getFirst());
        assertEquals(2, definition.getToolPolicy().maxToolCalls());
        assertEquals(30L, capability.getKnowledgeBaseId());
        assertEquals(31L, capability.getKnowledgeIndexVersionId());
        assertEquals(40L, capability.getEmbeddingModelInstanceId());
        assertEquals(5, capability.getTopK());
        assertEquals(2000, capability.getMaxResultTokens());
        assertEquals(8, capability.getTimeoutSeconds());
    }

    @Test
    void shouldRejectUnknownAgentType() {
        assertThrows(JSONException.class, () -> JSON.parseObject("{\"agentType\":\"UNKNOWN\"}", AgentRuntimeDefinition.class));
    }
}
