package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.memory.model.entity.ConversationSummarySnapshotEntity;
import com.lingxi.workbench.chat.memory.repo.ConversationSummarySnapshotRepo;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ConversationSummarySource;
import com.lingxi.workbench.chat.model.entity.ConversationEntity;
import com.lingxi.workbench.chat.repo.ConversationRepo;
import com.lingxi.workbench.workflow.model.ConversationSummaryMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationSummaryPersistenceServiceTest {

    @Test
    void shouldAppendSnapshotAndPromoteCurrentSummary() {
        ConversationRepo conversationRepo = mock(ConversationRepo.class);
        ConversationSummarySnapshotRepo snapshotRepo = mock(ConversationSummarySnapshotRepo.class);
        ConversationSummaryPersistenceService service = new ConversationSummaryPersistenceService(
                conversationRepo, snapshotRepo);
        ConversationEntity conversation = new ConversationEntity();
        conversation.setSummarySequenceNo(3);
        AssistantCompletedEvent event = event();
        ConversationSummarySource source = source();
        when(conversationRepo.lockOwnedById(10L, 7L)).thenReturn(Optional.of(conversation));
        when(conversationRepo.isSummaryPromoted(10L, 3, 7, "新摘要")).thenReturn(true);

        boolean saved = service.isSavedIfCurrent(event, source, "新摘要", event.memoryConfig().summary());

        ArgumentCaptor<ConversationSummarySnapshotEntity> snapshotCaptor =
                ArgumentCaptor.forClass(ConversationSummarySnapshotEntity.class);
        verify(snapshotRepo).insert(snapshotCaptor.capture());
        ConversationSummarySnapshotEntity snapshot = snapshotCaptor.getValue();
        assertTrue(saved);
        assertEquals(10L, snapshot.getConversationId());
        assertEquals(40L, snapshot.getApplicationVersionId());
        assertEquals(7, snapshot.getCoveredUserSequenceNo());
        assertEquals(30L, snapshot.getPromptVersionId());
        assertEquals(21L, snapshot.getModelInstanceId());
        verify(conversationRepo).isSummaryPromoted(10L, 3, 7, "新摘要");
    }

    @Test
    void shouldDiscardSummaryGeneratedFromStaleBase() {
        ConversationRepo conversationRepo = mock(ConversationRepo.class);
        ConversationSummarySnapshotRepo snapshotRepo = mock(ConversationSummarySnapshotRepo.class);
        ConversationSummaryPersistenceService service = new ConversationSummaryPersistenceService(
                conversationRepo, snapshotRepo);
        ConversationEntity conversation = new ConversationEntity();
        conversation.setSummarySequenceNo(9);
        when(conversationRepo.lockOwnedById(10L, 7L)).thenReturn(Optional.of(conversation));

        boolean saved = service.isSavedIfCurrent(event(), source(), "过期摘要", event().memoryConfig().summary());

        assertFalse(saved);
        verify(snapshotRepo, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(conversationRepo, never()).isSummaryPromoted(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static AssistantCompletedEvent event() {
        MemoryConfig config = MemoryConfig.builder()
                .summary(ConversationSummaryMemoryConfig.builder()
                        .enabled(true)
                        .chatModelInstanceId(21L)
                        .promptVersionId(30L)
                        .build())
                .build();
        return new AssistantCompletedEvent(7L, new ChatTurnBO(10L, 11L, 12L, 9), 40L, config);
    }

    private static ConversationSummarySource source() {
        return new ConversationSummarySource("旧摘要", 3, 7, List.of(
                new ChatMemoryMessageBO(5, MessageRole.USER, "问题"),
                new ChatMemoryMessageBO(6, MessageRole.ASSISTANT, "回答"),
                new ChatMemoryMessageBO(7, MessageRole.USER, "继续"),
                new ChatMemoryMessageBO(8, MessageRole.ASSISTANT, "完成")));
    }
}
