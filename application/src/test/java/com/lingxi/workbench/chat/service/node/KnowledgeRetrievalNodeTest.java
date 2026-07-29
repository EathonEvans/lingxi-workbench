package com.lingxi.workbench.chat.service.node;

import com.lingxi.workbench.chat.common.enums.RetrievalStatus;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.knowledge.service.KnowledgeSearchService;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class KnowledgeRetrievalNodeTest {

    @Test
    void shouldUsePublishedAgentVersionRetrievalConfig() {
        AtomicBoolean searchInvoked = new AtomicBoolean();
        EmbeddingModelRuntimeConfig embeddingConfig =
                EmbeddingModelRuntimeConfig.builder()
                        .instanceId(40L)
                        .build();
        KnowledgeSearchService knowledgeSearchService =
                (query, knowledgeBaseId, config, topK, threshold) -> {
                    assertEquals("改写后的独立问题", query);
                    assertEquals(30L, knowledgeBaseId);
                    assertEquals(embeddingConfig, config);
                    assertEquals(5, topK);
                    assertEquals(0.65D, threshold);
                    searchInvoked.set(true);
                    return List.of();
                };
        ModelRuntimeConfigService modelRuntimeConfigService =
                embeddingModelService(embeddingConfig);

        RuntimeVariables variables = new RuntimeVariables();
        variables.put(RuntimeVariableAccessors.USER_QUERY, "原始问题");
        variables.put(
                RuntimeVariableAccessors.KNOWLEDGE_RETRIEVAL_QUERY,
                "改写后的独立问题");
        ModelChatContext context = ModelChatContext.builder()
                .runtimeVariables(variables)
                .knowledgeBaseId(30L)
                .embeddingModelInstanceId(40L)
                .retrievalTopK(5)
                .retrievalSimilarityThreshold(0.65D)
                .retrievalFailureStrategy("FAIL_REQUEST")
                .build();

        new KnowledgeRetrievalNode(
                knowledgeSearchService,
                modelRuntimeConfigService)
                .process(context);

        assertTrue(searchInvoked.get());
        assertEquals(RetrievalStatus.EMPTY, context.getRetrievalStatus());
    }

    @Test
    void shouldSkipRetrievalWhenAgentTypeDoesNotBindKnowledge() {
        KnowledgeSearchService knowledgeSearchService =
                (query, knowledgeBaseId, config, topK, threshold) -> {
                    fail("未绑定知识库时不应执行检索");
                    return List.of();
                };
        ModelRuntimeConfigService modelRuntimeConfigService =
                embeddingModelService(null);
        RuntimeVariables variables = new RuntimeVariables();
        variables.put(RuntimeVariableAccessors.USER_QUERY, "普通聊天");
        ModelChatContext context = ModelChatContext.builder()
                .runtimeVariables(variables)
                .build();

        new KnowledgeRetrievalNode(
                knowledgeSearchService,
                modelRuntimeConfigService)
                .process(context);

        assertEquals(RetrievalStatus.SKIPPED, context.getRetrievalStatus());
        assertEquals(List.of(), context.getKnowledgeChunks());
    }

    private static ModelRuntimeConfigService embeddingModelService(
            EmbeddingModelRuntimeConfig embeddingConfig) {
        return new ModelRuntimeConfigService() {
            @Override
            public ChatModelRuntimeConfig getDefaultChatModel() {
                throw new AssertionError("测试不应加载默认对话模型");
            }

            @Override
            public ChatModelRuntimeConfig getChatModelConfig(Long instanceId) {
                throw new AssertionError("测试不应加载对话模型");
            }

            @Override
            public EmbeddingModelRuntimeConfig getEmbeddingModelConfig(Long instanceId) {
                if (embeddingConfig == null) {
                    throw new AssertionError("未绑定知识库时不应加载Embedding模型");
                }
                assertEquals(embeddingConfig.getInstanceId(), instanceId);
                return embeddingConfig;
            }
        };
    }
}
