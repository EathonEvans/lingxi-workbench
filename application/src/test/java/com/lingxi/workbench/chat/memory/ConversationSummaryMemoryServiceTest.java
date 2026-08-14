package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ConversationSummarySource;
import com.lingxi.workbench.chat.model.bo.ConversationSummaryTrigger;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.workflow.model.ConversationSummaryMemoryConfig;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationSummaryMemoryServiceTest {

    @Test
    void shouldGenerateRollingSummaryFromPreviousSummaryAndNewRounds() {
        ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
        MemoryCompletionService completionService = mock(MemoryCompletionService.class);
        ConversationSummaryPersistenceService persistenceService = mock(ConversationSummaryPersistenceService.class);
        ConversationSummaryMemoryService service = new ConversationSummaryMemoryService(
                memoryService, completionService, persistenceService);
        AssistantCompletedEvent event = event();
        MemoryConfig config = event.memoryConfig();
        ConversationSummarySource source = new ConversationSummarySource(
                "用户正在处理退款", 3, 7, List.of(
                        new ChatMemoryMessageBO(5, MessageRole.USER, "订单号是A100"),
                        new ChatMemoryMessageBO(6, MessageRole.ASSISTANT, "已找到订单"),
                        new ChatMemoryMessageBO(7, MessageRole.USER, "继续退款"),
                        new ChatMemoryMessageBO(8, MessageRole.ASSISTANT, "已提交退款")));
        when(memoryService.loadSummarySource(
                7L, event.turn(), new ConversationSummaryTrigger(2, 500, 16_000))).thenReturn(Optional.of(source));
        when(completionService.complete(
                org.mockito.ArgumentMatchers.eq(config.summary().getPromptVersionId()),
                org.mockito.ArgumentMatchers.eq(config.getChatModelInstanceId()),
                anyMap()))
                .thenReturn("退款已提交");

        service.generate(event, config);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variablesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(completionService).complete(
                org.mockito.ArgumentMatchers.eq(30L), org.mockito.ArgumentMatchers.eq(21L), variablesCaptor.capture());
        String history = variablesCaptor.getValue().get("conversation_history");
        assertTrue(history.contains("已有会话摘要"));
        assertTrue(history.contains("用户正在处理退款"));
        assertTrue(history.contains("USER: 订单号是A100"));
        verify(persistenceService).isSavedIfCurrent(event, source, "退款已提交", config);
    }

    @Test
    void shouldSkipModelCallBeforeSummaryThreshold() {
        ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
        MemoryCompletionService completionService = mock(MemoryCompletionService.class);
        ConversationSummaryPersistenceService persistenceService = mock(ConversationSummaryPersistenceService.class);
        ConversationSummaryMemoryService service = new ConversationSummaryMemoryService(
                memoryService, completionService, persistenceService);
        AssistantCompletedEvent event = event();
        when(memoryService.loadSummarySource(
                7L, event.turn(), new ConversationSummaryTrigger(2, 500, 16_000))).thenReturn(Optional.empty());

        service.generate(event, event.memoryConfig());

        verify(completionService, never()).complete(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), anyMap());
        verify(persistenceService, never()).isSavedIfCurrent(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static AssistantCompletedEvent event() {
        MemoryConfig config = MemoryConfig.builder()
                .chatModelInstanceId(21L)
                .summary(ConversationSummaryMemoryConfig.builder()
                        .enabled(true)
                        .minRounds(2)
                        .minTokens(500)
                        .promptVersionId(30L)
                        .build())
                .build();
        return new AssistantCompletedEvent(7L, new ChatTurnBO(10L, 11L, 12L, 9), 40L, config);
    }
}
