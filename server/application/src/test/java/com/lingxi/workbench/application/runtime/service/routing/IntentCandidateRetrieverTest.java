package com.lingxi.workbench.application.runtime.service.routing;

import com.lingxi.workbench.application.runtime.model.IntentCandidateRequest;
import com.lingxi.workbench.application.runtime.model.IntentCandidateSet;
import com.lingxi.workbench.application.runtime.model.entity.RuntimeIntentTreeVersionEntity;
import com.lingxi.workbench.application.runtime.repo.IntentTreeRuntimeRepo;
import com.lingxi.workbench.application.runtime.repo.IntentVectorRuntimeRepo;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.workflow.model.intent.IntentDecisionFailureAction;
import com.lingxi.workbench.workflow.model.intent.IntentNodeDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentNodeType;
import com.lingxi.workbench.workflow.model.intent.IntentRoutingPolicy;
import com.lingxi.workbench.workflow.model.intent.IntentSlotDefinition;
import com.lingxi.workbench.workflow.model.intent.IntentSlotValueType;
import com.lingxi.workbench.workflow.model.intent.IntentTreeDefinition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentCandidateRetrieverTest {

    private final IntentTreeRuntimeRepo intentTreeRepo = mock(IntentTreeRuntimeRepo.class);
    private final IntentVectorRuntimeRepo vectorRepo = mock(IntentVectorRuntimeRepo.class);
    private final ModelRuntimeConfigService modelRuntimeConfigService = mock(ModelRuntimeConfigService.class);
    private final EmbeddingGateway embeddingGateway = mock(EmbeddingGateway.class);
    private final IntentCandidateRetriever retriever = new IntentCandidateRetriever(
            intentTreeRepo, vectorRepo, modelRuntimeConfigService, embeddingGateway);

    @Test
    void shouldReturnThresholdedCandidatesAndBoundedMetadataFromSingleRetrieval() {
        RuntimeIntentTreeVersionEntity version = version();
        EmbeddingModelRuntimeConfig modelConfig = mock(EmbeddingModelRuntimeConfig.class);
        float[] queryVector = new float[]{0.1F, 0.2F};
        when(intentTreeRepo.findVersion(30L, 40L)).thenReturn(Optional.of(version));
        when(modelRuntimeConfigService.getEmbeddingModelConfig(50L)).thenReturn(modelConfig);
        when(embeddingGateway.embed(List.of("我要退款"), modelConfig)).thenReturn(List.of(queryVector));
        when(vectorRepo.search(queryVector, 40L, 50L, 3)).thenReturn(List.of(
                new IntentVectorRuntimeRepo.IntentCandidateRow("refund-node", "refund", 0.92D),
                new IntentVectorRuntimeRepo.IntentCandidateRow("exchange-node", "exchange", 0.70D),
                new IntentVectorRuntimeRepo.IntentCandidateRow("other-node", "other", 0.40D)));

        IntentCandidateSet result = retriever.retrieve(new IntentCandidateRequest(30L, 40L, "我要退款"));

        assertThat(result.candidates()).extracting(candidate -> candidate.intentCode()).containsExactly("refund");
        assertThat(result.retrievedCandidates()).extracting(candidate -> candidate.intentCode())
                .containsExactly("refund", "exchange", "other");
        assertThat(result.candidates().getFirst().getParentPath()).containsExactly("售后服务");
        assertThat(result.candidates().getFirst().getPositiveExamples()).containsExactly("我要退款");
        assertThat(result.candidates().getFirst().getNegativeExamples()).containsExactly("我要换货");
        assertThat(result.candidates().getFirst().getSlots()).singleElement().satisfies(slot -> {
            assertThat(slot.code()).isEqualTo("orderNo");
            assertThat(slot.valueType()).isEqualTo(IntentSlotValueType.STRING);
            assertThat(slot.required()).isTrue();
        });
        assertThat(result.candidates().getFirst().getRank()).isEqualTo(1);
        verify(embeddingGateway, times(1)).embed(List.of("我要退款"), modelConfig);
        verify(vectorRepo, times(1)).search(queryVector, 40L, 50L, 3);
    }

    @Test
    void shouldRejectLegacyIntentTreeSchema() {
        RuntimeIntentTreeVersionEntity version = version();
        version.setDefinitionSnapshot(new IntentTreeDefinition(
                1, 50L, version.getDefinitionSnapshot().routingPolicy(), List.of()));
        when(intentTreeRepo.findVersion(30L, 40L)).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> retriever.retrieve(new IntentCandidateRequest(30L, 40L, "我要退款")))
                .isInstanceOf(com.lingxi.workbench.framework.exceptions.BusinessException.class)
                .hasMessage("意图树发布定义版本不受支持");
    }

    @Test
    void shouldRejectVectorRowOutsidePublishedLeafDefinition() {
        RuntimeIntentTreeVersionEntity version = version();
        EmbeddingModelRuntimeConfig modelConfig = mock(EmbeddingModelRuntimeConfig.class);
        float[] queryVector = new float[]{0.1F, 0.2F};
        when(intentTreeRepo.findVersion(30L, 40L)).thenReturn(Optional.of(version));
        when(modelRuntimeConfigService.getEmbeddingModelConfig(50L)).thenReturn(modelConfig);
        when(embeddingGateway.embed(List.of("未知问题"), modelConfig)).thenReturn(List.of(queryVector));
        when(vectorRepo.search(queryVector, 40L, 50L, 3)).thenReturn(List.of(
                new IntentVectorRuntimeRepo.IntentCandidateRow("stale-node", "deleted", 0.99D)));

        assertThatThrownBy(() -> retriever.retrieve(new IntentCandidateRequest(30L, 40L, "未知问题")))
                .isInstanceOf(com.lingxi.workbench.framework.exceptions.BusinessException.class)
                .hasMessage("意图向量索引与发布定义不一致");
    }

    private static RuntimeIntentTreeVersionEntity version() {
        RuntimeIntentTreeVersionEntity entity = new RuntimeIntentTreeVersionEntity();
        entity.setId(40L);
        entity.setIntentTreeId(30L);
        entity.setEmbeddingModelInstanceId(50L);
        entity.setDefinitionSnapshot(new IntentTreeDefinition(
                2,
                50L,
                IntentRoutingPolicy.builder()
                        .topK(3)
                        .recallThreshold(new BigDecimal("0.80"))
                        .chatModelInstanceId(51L)
                        .decisionPromptVersionId(52L)
                        .maxOutputTokens(512)
                        .timeoutSeconds(30)
                        .maxRepeatAttempts(1)
                        .decisionFailureAction(IntentDecisionFailureAction.DEFER)
                        .build(),
                List.of(
                        IntentNodeDefinition.builder().id("after-sale").code("after_sale")
                                .name("售后服务").nodeType(IntentNodeType.ROUTER).enabled(true).build(),
                        leaf("refund-node", "refund", "退款", "after-sale",
                                List.of("我要退款"), List.of("我要换货"),
                                List.of(IntentSlotDefinition.builder().code("orderNo").name("订单号")
                                        .valueType(IntentSlotValueType.STRING).required(true).build())),
                        leaf("exchange-node", "exchange", "换货", "after-sale", List.of(), List.of(), List.of()),
                        leaf("other-node", "other", "其他", null, List.of(), List.of(), List.of()))));
        return entity;
    }

    private static IntentNodeDefinition leaf(
            String id,
            String code,
            String name,
            String parentId,
            List<String> positiveExamples,
            List<String> negativeExamples,
            List<IntentSlotDefinition> slots) {
        return IntentNodeDefinition.builder()
                .id(id)
                .code(code)
                .parentId(parentId)
                .name(name)
                .description(name + "处理")
                .nodeType(IntentNodeType.LEAF)
                .positiveExamples(positiveExamples)
                .negativeExamples(negativeExamples)
                .slots(slots)
                .enabled(true)
                .build();
    }
}
