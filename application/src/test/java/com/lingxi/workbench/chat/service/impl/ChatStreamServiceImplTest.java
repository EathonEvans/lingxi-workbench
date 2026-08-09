package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.application.runtime.model.ApplicationChatExecutionRequest;
import com.lingxi.workbench.application.runtime.model.ApplicationChatResult;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.ApplicationRuntimeService;
import com.lingxi.workbench.application.runtime.service.ApplicationChatExecutionService;
import com.lingxi.workbench.application.runtime.service.ChatExecutionEventPublisher;
import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.model.request.ChatRequest;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.repo.ChatGenerationEventRepo;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION;

class ChatStreamServiceImplTest {

    private static final LoginUser LOGIN_USER =
            new LoginUser(7L, "tester", "测试用户", null);

    private static final AppRuntimeSnapshot APPLICATION_SNAPSHOT =
            new AppRuntimeSnapshot(20L, "support", 30L, applicationDefinition());

    private final ConversationService conversationService = mock(ConversationService.class);
    private final ConversationMessageRepo messageRepo = mock(ConversationMessageRepo.class);
    private final ChatGenerationEventRepo generationEventRepo = mock(ChatGenerationEventRepo.class);
    private final ApplicationRuntimeService applicationRuntimeService =
            mock(ApplicationRuntimeService.class);
    private final ApplicationChatExecutionService applicationChatExecutionService =
            mock(ApplicationChatExecutionService.class);
    private final TokenCountEstimator tokenCountEstimator = mock(TokenCountEstimator.class);

    private ChatStreamServiceImpl service;

    private static ApplicationDefinition applicationDefinition() {
        ApplicationAgentReference defaultAgent = new ApplicationAgentReference(40L, 41L, "默认助手", "处理通用请求");
        ApplicationCapabilityDefinition capability = ApplicationCapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.REACT_AGENT)
                .targetId(40L)
                .targetVersionId(41L)
                .build();
        return ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .defaultAgent(defaultAgent)
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(capability)).build())
                .build();
    }

    @BeforeEach
    void setUp() {
        when(applicationRuntimeService.loadRuntimeAppConfig("support"))
                .thenReturn(APPLICATION_SNAPSHOT);
        when(conversationService.startTurn(
                        7L, 10L, "client-001", "你好", null, 20L, 30L))
                .thenReturn(new ChatTurnBO(10L, 11L, 12L, 3));
        when(messageRepo.findOwnedAssistantMessage(12L, 7L))
                .thenReturn(Optional.of(assistant(MessageStatus.GENERATING, "")));
        when(generationEventRepo.findAfter(any(), anyLong())).thenReturn(List.of());

        service = new ChatStreamServiceImpl(conversationService, messageRepo, generationEventRepo,
                applicationRuntimeService, applicationChatExecutionService, event -> { }, tokenCountEstimator);
    }

    @Test
    void shouldResolvePublishedApplicationBeforeCreatingTurn() {
        ApplicationExecutionGate gate = prepareApplicationExecution("应用回答");

        service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();

        verify(applicationRuntimeService).loadRuntimeAppConfig("support");
        verify(conversationService).startTurn(
                7L, 10L, "client-001", "你好", null, 20L, 30L);

        gate.release();
    }

    @Test
    void shouldNotCreateMessageWhenApplicationCannotBeResolved() {
        when(applicationRuntimeService.loadRuntimeAppConfig("support"))
                .thenThrow(new BusinessException(
                        HttpStatusEnum.NOT_FOUND,
                        "AI应用不存在或已停用"));

        assertThrows(
                BusinessException.class,
                () -> service.start(createChatRequest(), LOGIN_USER));

        verify(conversationService, never()).startTurn(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRejectOversizedQueryBeforeCreatingTurn() {
        when(tokenCountEstimator.estimate("你好")).thenReturn(4001);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.start(createChatRequest(), LOGIN_USER));

        assertEquals(HttpStatusEnum.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("当前输入约4001 Token，超过应用上限4000 Token，请缩短后重试", exception.getMessage());
        verify(conversationService, never()).startTurn(any(), any(), any(), any(), any(), any(), any());
        verify(applicationChatExecutionService, never()).execute(any());
    }

    @Test
    void shouldPublishAndPersistApplicationAnswer() {
        ApplicationExecutionGate gate = prepareApplicationExecution("完整回答");

        service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();
        CompletableFuture<List<ChatGenerationEventBO>> eventsFuture =
                service.resume(12L, 0, 7L)
                        .collectList()
                        .toFuture();
        gate.release();

        List<ChatGenerationEventBO> events = eventsFuture
                .orTimeout(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)
                .join();

        assertEquals(List.of(
                ChatGenerationEventBO.chunk(1, "完整回答"),
                ChatGenerationEventBO.completed()), events);
        verify(conversationService).completeAssistant(
                12L,
                "完整回答",
                TokenUsage.of(12, 4, 16),
                99L);
    }

    @Test
    void shouldCancelOnlyOnExplicitRequest() {
        ApplicationExecutionGate gate = prepareApplicationExecution("不会返回");

        service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();
        service.cancel(12L, 7L);

        verify(conversationService).cancelAssistant(
                12L,
                "",
                TokenUsage.empty());
    }

    @Test
    void shouldInterruptRunningTaskWhenApplicationShutsDown() {
        ApplicationExecutionGate gate = prepareApplicationExecution("不会返回");

        service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();
        service.shutdownRunningTasks();

        verify(conversationService).interruptAssistant(
                12L,
                "",
                TokenUsage.empty());
    }

    @Test
    void shouldRestoreCompletedAnswerFromDatabaseWhenTaskIsNotRunning() {
        when(messageRepo.findOwnedAssistantMessage(12L, 7L))
                .thenReturn(Optional.of(assistant(
                        MessageStatus.COMPLETED,
                        "数据库中的完整回答")));

        List<ChatGenerationEventBO> events = service.resume(12L, 0, 7L)
                .collectList()
                .block();

        assertEquals(List.of(
                ChatGenerationEventBO.snapshot("数据库中的完整回答"),
                ChatGenerationEventBO.completed()), events);
        assertEquals(ChatGenerationEventType.COMPLETED, events.get(1).type());
    }

    @Test
    void shouldRestoreToolEventsFromRedisBeforeDatabaseTerminalState() {
        AgentToolExecutionEvent tool = AgentToolExecutionEvent.builder()
                .invocationId("tool-1")
                .toolCode("system_current_time")
                .displayName("查询当前时间")
                .status(AgentToolExecutionStatus.SUCCEEDED)
                .durationMillis(18L)
                .build();
        when(generationEventRepo.findAfter(12L, 1L)).thenReturn(List.of(ChatGenerationEventBO.tool(2, tool)));
        when(messageRepo.findOwnedAssistantMessage(12L, 7L)).thenReturn(Optional.of(assistant(MessageStatus.COMPLETED, "数据库中的完整回答")));

        List<ChatGenerationEventBO> events = service.resume(12L, 1L, 7L).collectList().block();

        assertEquals(ChatGenerationEventType.TOOL, events.get(0).type());
        assertEquals(AgentToolExecutionStatus.SUCCEEDED, events.get(0).tool().getStatus());
        assertEquals(ChatGenerationEventType.SNAPSHOT, events.get(1).type());
        assertEquals(ChatGenerationEventType.COMPLETED, events.get(2).type());
    }

    /**
     * 让同步 Application 目标执行停在后台线程，测试可以稳定观察“任务运行中”的状态，
     * 避免依赖线程调度快慢产生偶发失败。
     */
    private ApplicationExecutionGate prepareApplicationExecution(String answer) {
        ApplicationExecutionGate gate = new ApplicationExecutionGate();
        doAnswer(invocation -> {
            ApplicationChatExecutionRequest request = invocation.getArgument(0);
            assertEquals(APPLICATION_SNAPSHOT, request.getApplication());
            assertEquals("你好", request.getQuery());
            assertEquals(LOGIN_USER, request.getLoginUser());
            assertEquals(new ChatTurnBO(10L, 11L, 12L, 3), request.getTurn());
            gate.markStarted();
            gate.awaitRelease();
            ChatExecutionEventPublisher eventPublisher = request.getEventPublisher();
            eventPublisher.publishText(new ChatStreamChunk(answer, TokenUsage.of(12, 4, 16)));
            return new ApplicationChatResult(answer, TokenUsage.of(12, 4, 16), List.of(), 99L);
        }).when(applicationChatExecutionService).execute(any(ApplicationChatExecutionRequest.class));
        return gate;
    }

    private static ConversationMessageEntity assistant(MessageStatus status, String content) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setId(12L);
        entity.setRole(MessageRole.ASSISTANT);
        entity.setStatus(status);
        entity.setContent(content);
        return entity;
    }

    private static ChatRequest createChatRequest() {
        return ChatRequest.builder()
                .conversationId("10")
                .clientMessageId("client-001")
                .query("你好")
                .applicationCode("support")
                .build();
    }

    /**
     * 测试专用的两阶段门闩：started 表示 Workflow 已进入后台线程，
     * release 由测试决定何时允许它返回最终回答。
     */
    private static final class ApplicationExecutionGate {

        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        void markStarted() {
            started.countDown();
        }

        void awaitStarted() {
            assertTrue(await(started), "Workflow 未在预期时间内启动");
        }

        void release() {
            release.countDown();
        }

        void awaitRelease() {
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("等待测试释放 Workflow 超时");
                }
            } catch (InterruptedException exception) {
                // cancel/shutdown 会主动 dispose 后台订阅；线程中断正是这两个测试期望的停止方式。
                Thread.currentThread().interrupt();
            }
        }

        private static boolean await(CountDownLatch latch) {
            try {
                return latch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
