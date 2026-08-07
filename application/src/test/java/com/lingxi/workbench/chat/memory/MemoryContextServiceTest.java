package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.chat.memory.config.MemoryEmbeddingModelResolver;
import com.lingxi.workbench.chat.memory.repo.InteractionRecordVectorRepo;
import com.lingxi.workbench.chat.memory.repo.UserProfileRepo;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.workflow.model.InteractionRecordMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryContextServiceTest {

    @Test
    void shouldApplyRetentionDaysWhenRecallingInteractionRecords() {
        UserProfileRepo profileRepo = mock(UserProfileRepo.class);
        InteractionRecordVectorRepo vectorRepo = mock(InteractionRecordVectorRepo.class);
        EmbeddingGateway embeddingGateway = mock(EmbeddingGateway.class);
        MemoryEmbeddingModelResolver embeddingResolver = mock(MemoryEmbeddingModelResolver.class);
        EmbeddingModelRuntimeConfig runtimeConfig = EmbeddingModelRuntimeConfig.builder().instanceId(88L).build();
        when(embeddingResolver.resolve(88L)).thenReturn(runtimeConfig);
        when(embeddingGateway.embed(List.of("退款进度"), runtimeConfig)).thenReturn(List.of(new float[]{0.1F, 0.2F}));
        MemoryContextService service = new MemoryContextService(
                profileRepo, vectorRepo, embeddingGateway, embeddingResolver);
        MemoryConfig config = MemoryConfig.builder()
                .interactionRecord(InteractionRecordMemoryConfig.builder()
                        .enabled(true)
                        .embeddingModelInstanceId(88L)
                        .topK(5)
                        .retentionDays(90)
                        .build())
                .build();

        String context = service.build(7L, "退款进度", config);

        assertThat(context).isEmpty();
        verify(vectorRepo).findSimilar(eq(7L), eq(88L), aryEq(new float[]{0.1F, 0.2F}), eq(90), eq(5));
    }
}
