package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.MessageTokenUsageBO;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.model.request.StartChatRequest;
import com.lingxi.workbench.chat.model.vo.ChatStartVO;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.StreamingChatGateway;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatStreamServiceImplTest {

    private final StreamingChatGateway gateway = mock(StreamingChatGateway.class);
    private final ModelRuntimeConfigService configService = mock(ModelRuntimeConfigService.class);
    private final ConversationService conversationService = mock(ConversationService.class);
    private final ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
    private final ConversationMessageRepo messageRepo = mock(ConversationMessageRepo.class);

    private ChatStreamServiceImpl service;

    @BeforeEach
    void setUp() {
        ChatModelRuntimeConfig config = new ChatModelRuntimeConfig(
                1L, "https://api.deepseek.com", "secret", "deepseek-chat",
                0.7, null, null, List.of(), 60, 1);
        when(configService.getDefaultChatModel()).thenReturn(config);
        when(conversationService.startTurn(7L, 10L, "client-001", "你好", 1L))
                .thenReturn(new ChatTurnBO(10L, 11L, 12L, 3));
        when(memoryService.getWindowMemory(7L, 10L, 3)).thenReturn(List.of(
                new ChatMemoryMessageBO(MessageRole.USER, "上一问"),
                new ChatMemoryMessageBO(MessageRole.ASSISTANT, "上一答")
        ));
        service = new ChatStreamServiceImpl(
                gateway, configService, conversationService, memoryService, messageRepo);
    }

    @Test
    void shouldContinueGenerationAfterSseObserverDisconnects() {
        Sinks.Many<ChatStreamChunk> modelSink = Sinks.many().unicast().onBackpressureBuffer();
        when(gateway.stream(any())).thenReturn(modelSink.asFlux());

        ChatStartVO start = service.start(
                new StartChatRequest("10", "client-001", "你好"), 7L);
        when(messageRepo.findOwnedAssistantMessage(12L, 7L))
                .thenReturn(Optional.of(assistant(MessageStatus.GENERATING, "")));
        List<ChatGenerationEventBO> received = new ArrayList<>();
        Disposable observer = service.resume(12L, 0, 7L).subscribe(received::add);
        modelSink.tryEmitNext(new ChatStreamChunk("你"));
        observer.dispose();
        modelSink.tryEmitNext(new ChatStreamChunk("好"));
        modelSink.tryEmitNext(new ChatStreamChunk("", 12, 2, 14));
        modelSink.tryEmitComplete();

        assertEquals("10", start.conversationId());
        assertEquals(List.of(ChatGenerationEventBO.chunk(1, "你")), received);
        verify(conversationService).completeAssistant(
                12L, "你好", new MessageTokenUsageBO(12, 2, 14));
        ArgumentCaptor<ChatStreamCommand> commandCaptor =
                ArgumentCaptor.forClass(ChatStreamCommand.class);
        verify(gateway).stream(commandCaptor.capture());
        assertEquals("上一问", commandCaptor.getValue().history().get(0).content());
        assertEquals("上一答", commandCaptor.getValue().history().get(1).content());
    }

    @Test
    void shouldReplayOnlyChunksAfterClientSequence() {
        Sinks.Many<ChatStreamChunk> modelSink = Sinks.many().unicast().onBackpressureBuffer();
        when(gateway.stream(any())).thenReturn(modelSink.asFlux());
        service.start(new StartChatRequest("10", "client-001", "你好"), 7L);
        modelSink.tryEmitNext(new ChatStreamChunk("你"));
        when(messageRepo.findOwnedAssistantMessage(12L, 7L))
                .thenReturn(Optional.of(assistant(MessageStatus.GENERATING, "")));
        List<ChatGenerationEventBO> resumed = new ArrayList<>();
        service.resume(12L, 1, 7L).subscribe(resumed::add);

        modelSink.tryEmitNext(new ChatStreamChunk("好"));
        modelSink.tryEmitComplete();

        assertEquals(List.of(
                ChatGenerationEventBO.chunk(2, "好"),
                ChatGenerationEventBO.completed()), resumed);
    }

    @Test
    void shouldCancelOnlyOnExplicitRequest() {
        Sinks.Many<ChatStreamChunk> modelSink = Sinks.many().unicast().onBackpressureBuffer();
        when(gateway.stream(any())).thenReturn(modelSink.asFlux());
        service.start(new StartChatRequest("10", "client-001", "你好"), 7L);
        modelSink.tryEmitNext(new ChatStreamChunk("部"));
        when(messageRepo.findOwnedAssistantMessage(12L, 7L))
                .thenReturn(Optional.of(assistant(MessageStatus.GENERATING, "")));

        service.cancel(12L, 7L);

        verify(conversationService).cancelAssistant(
                12L, "部", MessageTokenUsageBO.empty());
    }

    @Test
    void shouldRestoreCompletedAnswerFromDatabaseWhenTaskIsNotRunning() {
        when(messageRepo.findOwnedAssistantMessage(12L, 7L))
                .thenReturn(Optional.of(assistant(MessageStatus.COMPLETED, "完整回答")));

        List<ChatGenerationEventBO> events = service.resume(12L, 0, 7L)
                .collectList()
                .block();

        assertEquals(List.of(
                ChatGenerationEventBO.snapshot("完整回答"),
                ChatGenerationEventBO.completed()), events);
        assertEquals(ChatGenerationEventType.COMPLETED, events.get(1).type());
    }

    @Test
    void shouldFailAssistantWhenTaskPreparationFails() {
        when(memoryService.getWindowMemory(7L, 10L, 3))
                .thenThrow(new IllegalStateException("memory unavailable"));

        assertThrows(IllegalStateException.class, () -> service.start(
                new StartChatRequest("10", "client-001", "你好"), 7L));

        verify(conversationService).failAssistant(
                12L,
                "",
                "GENERATION_START_ERROR",
                "模型生成任务启动失败",
                MessageTokenUsageBO.empty());
    }

    @Test
    void shouldInterruptRunningTaskWhenApplicationShutsDown() {
        Sinks.Many<ChatStreamChunk> modelSink = Sinks.many().unicast().onBackpressureBuffer();
        when(gateway.stream(any())).thenReturn(modelSink.asFlux());
        service.start(new StartChatRequest("10", "client-001", "你好"), 7L);
        modelSink.tryEmitNext(new ChatStreamChunk("部分回答"));

        service.shutdownRunningTasks();

        verify(conversationService).interruptAssistant(
                12L, "部分回答", MessageTokenUsageBO.empty());
    }

    private static ConversationMessageEntity assistant(
            MessageStatus status, String content) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setId(12L);
        entity.setRole(MessageRole.ASSISTANT);
        entity.setStatus(status);
        entity.setContent(content);
        return entity;
    }
}
