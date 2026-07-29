package com.lingxi.workbench.application.runtime.model;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeDefinitionJsonTest {

    @Test
    void shouldReadAdminAgentVersionSnapshotContract() {
        String json = """
                {
                  "schemaVersion": 1,
                  "agentType": "RAG_CHAT",
                  "chatModelInstanceId": 10,
                  "answerPromptVersionId": 20,
                  "knowledge": {
                    "knowledgeBaseId": 30,
                    "embeddingModelInstanceId": 40,
                    "bindingMode": "LATEST",
                    "priority": 0
                  },
                  "memoryPolicy": {
                    "enabled": true,
                    "maxRounds": 10,
                    "maxTokens": 4000
                  },
                  "rewritePolicy": {
                    "enabled": false
                  },
                  "retrievalPolicy": {
                    "topK": 5,
                    "similarityThreshold": 0.65,
                    "maxContextTokens": 4000,
                    "emptyResultStrategy": "RETURN_EMPTY_HINT",
                    "failureStrategy": "FAIL_REQUEST"
                  },
                  "generationPolicy": {
                    "temperature": 0.2,
                    "topP": 0.9,
                    "maxOutputTokens": 2048,
                    "timeoutSeconds": 60,
                    "maxRetries": 1
                  },
                  "runtimePolicy": {
                    "totalTimeoutSeconds": 90,
                    "maxModelCalls": 3,
                    "maxInputTokens": 12000,
                    "maxOutputTokens": 2048
                  }
                }
                """;

        AgentRuntimeDefinition definition =
                JSON.parseObject(json, AgentRuntimeDefinition.class);

        assertEquals("RAG_CHAT", definition.getAgentType());
        assertEquals(20L, definition.resolveSystemPromptVersionId());
        assertEquals(30L, definition.getKnowledge().knowledgeBaseId());
        assertEquals(5, definition.getRetrievalPolicy().getTopK());
        assertTrue(definition.getMemoryPolicy().enabled());
    }

    @Test
    void shouldReadSchemaV2SystemAndUserPromptBindings() {
        AgentRuntimeDefinition definition = JSON.parseObject(
                """
                        {
                          "schemaVersion": 2,
                          "agentType": "RAG_CHAT",
                          "chatModelInstanceId": 10,
                          "systemPromptVersionId": 20,
                          "userPromptVersionId": 21
                        }
                        """,
                AgentRuntimeDefinition.class);

        assertEquals(20L, definition.resolveSystemPromptVersionId());
        assertEquals(21L, definition.getUserPromptVersionId());
    }
}
