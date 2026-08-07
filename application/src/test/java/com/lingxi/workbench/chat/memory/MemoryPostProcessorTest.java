package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.workflow.model.ConversationSummaryMemoryConfig;
import com.lingxi.workbench.workflow.model.InteractionRecordMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.UserProfileMemoryConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MemoryPostProcessorTest {

    private final ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
    private final ConversationSummaryMemoryService summaryService = mock(ConversationSummaryMemoryService.class);
    private final UserProfileMemoryService profileService = mock(UserProfileMemoryService.class);
    private final InteractionRecordMemoryService interactionService = mock(InteractionRecordMemoryService.class);
    private final MemoryPostProcessor processor = new MemoryPostProcessor(
            memoryService, summaryService, profileService, interactionService);

    @Test
    void shouldIsolateEachMemoryOperationFailure() {
        AssistantCompletedEvent event = event();
        List<ChatMemoryMessageBO> messages = List.of(
                new ChatMemoryMessageBO(1, MessageRole.USER, "问题"),
                new ChatMemoryMessageBO(2, MessageRole.ASSISTANT, "回答"));
        when(memoryService.getWindowMemory(any(), any(), any(), anyInt(), anyInt())).thenReturn(messages);
        doThrow(new IllegalStateException("summary failed")).when(summaryService).generate(any(), any());

        assertDoesNotThrow(() -> processor.onAssistantCompleted(event));

        verify(summaryService).generate(any(), any());
        verify(profileService).extract(any(), anyString(), any());
        verify(interactionService).extract(any(), anyString(), any());
    }

    @Test
    void shouldKeepSummaryIndependentWhenHistoryLoadingFails() {
        AssistantCompletedEvent event = event();
        when(memoryService.getWindowMemory(any(), any(), any(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("history failed"));

        assertDoesNotThrow(() -> processor.onAssistantCompleted(event));

        verify(summaryService).generate(event, event.memoryConfig().summary());
        verifyNoInteractions(profileService, interactionService);
    }

    @Test
    void shouldSkipDisabledMemoryWithoutLoadingHistory() {
        AssistantCompletedEvent event = new AssistantCompletedEvent(
                7L, new ChatTurnBO(10L, 11L, 12L, 8), 30L, MemoryConfig.disabled());

        processor.onAssistantCompleted(event);

        verify(memoryService, never()).getWindowMemory(any(), any(), any(), anyInt(), anyInt());
        verifyNoInteractions(summaryService, profileService, interactionService);
    }

    private static AssistantCompletedEvent event() {
        MemoryConfig config = MemoryConfig.builder()
                .profile(UserProfileMemoryConfig.builder()
                        .enabled(true)
                        .chatModelInstanceId(21L)
                        .promptVersionId(31L)
                        .build())
                .interactionRecord(InteractionRecordMemoryConfig.builder()
                        .enabled(true)
                        .embeddingModelInstanceId(20L)
                        .chatModelInstanceId(21L)
                        .promptVersionId(32L)
                        .build())
                .summary(ConversationSummaryMemoryConfig.builder()
                        .enabled(true)
                        .chatModelInstanceId(21L)
                        .promptVersionId(30L)
                        .build())
                .build();
        return new AssistantCompletedEvent(7L, new ChatTurnBO(10L, 11L, 12L, 8), 30L, config);
    }
}
