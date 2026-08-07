package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.chat.memory.config.MemoryEmbeddingModelResolver;
import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.infra.ai.embedding.EmbeddingGateway;
import com.lingxi.workbench.workflow.model.InteractionRecordMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InteractionRecordMemoryServiceTest {

    @Test
    void shouldReturnExistingRecordWithoutRepeatedModelOrEmbeddingCalls() {
        MemoryCompletionService completionService = mock(MemoryCompletionService.class);
        MemoryEmbeddingModelResolver embeddingResolver = mock(MemoryEmbeddingModelResolver.class);
        EmbeddingGateway embeddingGateway = mock(EmbeddingGateway.class);
        InteractionMemoryStoreService storeService = mock(InteractionMemoryStoreService.class);
        InteractionRecordMemoryService service = new InteractionRecordMemoryService(
                completionService, embeddingResolver, embeddingGateway, storeService);
        AssistantCompletedEvent event = new AssistantCompletedEvent(
                7L, new ChatTurnBO(10L, 11L, 12L, 8), 30L, MemoryConfig.disabled());
        when(storeService.findExistingRecordId(12L)).thenReturn(Optional.of(99L));

        Long recordId = service.extract(event, "USER: hello", InteractionRecordMemoryConfig.disabled());

        assertEquals(99L, recordId);
        verify(storeService).findExistingRecordId(12L);
        verifyNoInteractions(completionService, embeddingResolver, embeddingGateway);
    }
}
