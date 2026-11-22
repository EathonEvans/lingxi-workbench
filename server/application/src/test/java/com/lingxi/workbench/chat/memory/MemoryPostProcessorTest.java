package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.observability.service.StageObserver;
import com.lingxi.workbench.workflow.model.ConversationSummaryMemoryConfig;
import com.lingxi.workbench.workflow.model.InteractionRecordMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.workflow.model.UserProfileMemoryConfig;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MemoryPostProcessorTest {

    private final MemoryEventResolver eventResolver = mock(MemoryEventResolver.class);
    private final ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
    private final ConversationSummaryMemoryService summaryService = mock(ConversationSummaryMemoryService.class);
    private final UserProfileMemoryService profileService = mock(UserProfileMemoryService.class);
    private final InteractionRecordMemoryService interactionService = mock(InteractionRecordMemoryService.class);
    private final MemoryPostProcessor processor = new MemoryPostProcessor(
            eventResolver, memoryService, summaryService, profileService, interactionService,
            new StageObserver(ObservationRegistry.NOOP));

    @Test
    void shouldPropagateSummaryFailureForRocketMqRetry() {
        ConversationCompletedMessage message = message();
        AssistantCompletedEvent event = event();
        when(eventResolver.resolve(message)).thenReturn(event);
        doThrow(new IllegalStateException("summary failed")).when(summaryService).generate(event, event.memoryConfig());

        assertThrows(IllegalStateException.class, () -> processor.processSummary(message));

        verifyNoInteractions(memoryService, profileService, interactionService);
    }

    @Test
    void shouldProcessProfileWithoutInvokingOtherPipelines() {
        ConversationCompletedMessage message = message();
        AssistantCompletedEvent event = event();
        List<ChatMemoryMessageBO> messages = List.of(
                new ChatMemoryMessageBO(1, MessageRole.USER, "问题"),
                new ChatMemoryMessageBO(2, MessageRole.ASSISTANT, "回答"));
        when(eventResolver.resolve(message)).thenReturn(event);
        when(memoryService.getWindowMemory(any(), any(), any(), anyInt(), anyInt())).thenReturn(messages);

        processor.processProfile(message);

        verify(profileService).extract(event, "USER: 问题\nASSISTANT: 回答\n", event.memoryConfig());
        verifyNoInteractions(summaryService, interactionService);
    }

    @Test
    void shouldSkipDisabledProfileWithoutLoadingHistory() {
        ConversationCompletedMessage message = message();
        AssistantCompletedEvent event = new AssistantCompletedEvent(
                7L, new ChatTurnBO(10L, 11L, 12L, 8), 30L, MemoryConfig.disabled());
        when(eventResolver.resolve(message)).thenReturn(event);

        processor.processProfile(message);

        verify(memoryService, never()).getWindowMemory(any(), any(), any(), anyInt(), anyInt());
        verifyNoInteractions(summaryService, profileService, interactionService);
    }

    private static ConversationCompletedMessage message() {
        return new ConversationCompletedMessage("conversation-completed:12", 10L, 12L, 30L);
    }

    private static AssistantCompletedEvent event() {
        MemoryConfig config = MemoryConfig.builder()
                .chatModelInstanceId(21L)
                .embeddingModelInstanceId(20L)
                .profile(UserProfileMemoryConfig.builder().enabled(true).promptVersionId(31L).build())
                .interactionRecord(InteractionRecordMemoryConfig.builder().enabled(true).promptVersionId(32L).build())
                .summary(ConversationSummaryMemoryConfig.builder().enabled(true).promptVersionId(30L).build())
                .build();
        return new AssistantCompletedEvent(7L, new ChatTurnBO(10L, 11L, 12L, 8), 30L, config);
    }
}
