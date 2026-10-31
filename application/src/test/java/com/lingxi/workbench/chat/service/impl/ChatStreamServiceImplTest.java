package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.application.runtime.model.AppExecutionRequest;
import com.lingxi.workbench.application.runtime.model.ApplicationChatResult;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.vo.ApplicationAssistantIdentityVO;
import com.lingxi.workbench.application.runtime.model.InteractionSessionContext;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.service.app.ApplicationVersionService;
import com.lingxi.workbench.application.runtime.service.app.AppRuntime;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionFailureClassifier;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import com.lingxi.workbench.application.runtime.service.interaction.HitlDecisionTxService;
import com.lingxi.workbench.application.runtime.repo.ExecutionCheckpointRepo;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.event.ConversationEventPublisher;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ConversationTurnStartBO;
import com.lingxi.workbench.chat.model.bo.StartConversationTurnBO;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.model.request.ChatRequest;
import com.lingxi.workbench.chat.model.vo.ChatStartVO;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.repo.ChatGenerationEventRepo;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.chat.service.GenerationTaskRegistry;
import com.lingxi.workbench.chat.service.SuggestionService;
import com.lingxi.workbench.application.runtime.service.trigger.TriggerRunTxService;
import com.lingxi.workbench.notification.service.NotificationService;
import com.lingxi.workbench.auth.repo.UserRepo;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.agentruntime.model.AgentRuntimeLimitException;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.observability.service.StageObserver;
import com.lingxi.workbench.observability.service.TraceRun;
import com.lingxi.workbench.observability.service.TracingService;
import io.micrometer.observation.ObservationRegistry;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.AgentCollaborationDefinition;
import com.lingxi.workbench.workflow.model.CapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationContextPolicy;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy;
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
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
    private final ApplicationVersionService appVersionService =
            mock(ApplicationVersionService.class);
    private final AppRuntime appRuntime =
            mock(AppRuntime.class);
    private final ConversationEventPublisher eventPublisher = mock(ConversationEventPublisher.class);
    private final TokenCountEstimator tokenCountEstimator = mock(TokenCountEstimator.class);
    private final TracingService aiTracingService = mock(TracingService.class);
    private final TraceRun traceRun = mock(TraceRun.class);
    private final StageObserver aiStageObserver = new StageObserver(ObservationRegistry.NOOP);
    private final InteractionSessionRepo interactionRepo = mock(InteractionSessionRepo.class);
    private final ExecutionCheckpointRepo checkpointRepo = mock(ExecutionCheckpointRepo.class);
    private final UserRepo userRepo = mock(UserRepo.class);
    private final HitlDecisionTxService hitlDecisionTxService = mock(HitlDecisionTxService.class);
    private final GenerationTaskRegistry generationTaskRegistry = mock(GenerationTaskRegistry.class);
    private final SuggestionService suggestionService = mock(SuggestionService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final TriggerRunTxService triggerRunTxService = mock(TriggerRunTxService.class);

    private ChatStreamServiceImpl service;

    private static ApplicationDefinition applicationDefinition() {
        CapabilityDefinition capability = CapabilityDefinition.builder()
                .code("default_agent")
                .type(ApplicationCapabilityType.AGENT)
                .targetId(40L)
                .targetVersionId(41L)
                .build();
        return ApplicationDefinition.builder()
                .schemaVersion(APPLICATION_SCHEMA_VERSION)
                .collaboration(new AgentCollaborationDefinition(
                        new ApplicationAgentReference(40L, 41L, "默认助手", "负责收口"),
                        List.of(), List.of(), List.of()))
                .memoryConf(MemoryConfig.disabled())
                .contextPolicy(ApplicationContextPolicy.defaults())
                .fallback(new FallbackAgent("default_agent"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(List.of(capability)).build())
                .runtimePolicy(ApplicationRuntimePolicy.defaults())
                .build();
    }

    @BeforeEach
    void setUp() {
        when(appVersionService.loadRuntimeAppConfig("support"))
                .thenReturn(APPLICATION_SNAPSHOT);
        when(appVersionService.resolveAssistantIdentity(20L, 30L))
                .thenReturn(new ApplicationAssistantIdentityVO("支持助手", null));
        when(conversationService.startTurn(any(StartConversationTurnBO.class)))
                .thenReturn(new ConversationTurnStartBO(new ChatTurnBO(10L, 11L, 12L, 3), true));
        when(messageRepo.findOwnedAssistantMessage(12L, 7L))
                .thenReturn(Optional.of(assistant(MessageStatus.GENERATING, "")));
        when(messageRepo.findById(12L))
                .thenReturn(Optional.of(assistant(MessageStatus.GENERATING, "")));
        when(generationEventRepo.findAfter(any(), anyLong())).thenReturn(List.of());
        when(aiTracingService.start(any(), any(), any())).thenReturn(traceRun);
        when(generationTaskRegistry.register(anyLong(), any(Runnable.class), any(Runnable.class)))
                .thenReturn(GenerationTaskRegistry.RegistrationResult.ACQUIRED);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get())
                .when(traceRun).scoped(org.mockito.ArgumentMatchers.<Supplier<?>>any());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(traceRun).scoped(org.mockito.ArgumentMatchers.<Runnable>any());

        service = new ChatStreamServiceImpl(conversationService, messageRepo, generationEventRepo,
                appVersionService, appRuntime, eventPublisher, tokenCountEstimator,
                aiTracingService, aiStageObserver, new ExecutionFailureClassifier(), interactionRepo, checkpointRepo,
                userRepo, hitlDecisionTxService, generationTaskRegistry, suggestionService,
                notificationService, triggerRunTxService);
    }

    @Test
    void shouldResolvePublishedApplicationBeforeCreatingTurn() {
        ApplicationExecutionGate gate = prepareApplicationExecution("应用回答");

        ChatStartVO result = service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();

        verify(appVersionService).loadRuntimeAppConfig("support");
        ArgumentCaptor<StartConversationTurnBO> inputCaptor = ArgumentCaptor.forClass(StartConversationTurnBO.class);
        verify(conversationService).startTurn(inputCaptor.capture());
        StartConversationTurnBO input = inputCaptor.getValue();
        assertEquals(7L, input.getUserId());
        assertEquals("10", input.getConversationId());
        assertEquals("client-001", input.getClientMessageId());
        assertEquals(20L, input.getApplicationId());
        assertEquals(30L, input.getApplicationVersionId());
        assertEquals("支持助手", result.assistant().name());

        gate.release();
    }

    @Test
    void shouldNotCreateMessageWhenApplicationCannotBeResolved() {
        when(appVersionService.loadRuntimeAppConfig("support"))
                .thenThrow(new BusinessException(
                        HttpStatusEnum.NOT_FOUND,
                        "AI应用不存在或已停用"));

        assertThrows(
                BusinessException.class,
                () -> service.start(createChatRequest(), LOGIN_USER));

        verify(conversationService, never()).startTurn(any(StartConversationTurnBO.class));
    }

    @Test
    void shouldRejectOversizedQueryBeforeCreatingTurn() {
        when(tokenCountEstimator.estimate("你好")).thenReturn(4001);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.start(createChatRequest(), LOGIN_USER));

        assertEquals(HttpStatusEnum.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("当前输入约4001 Token，超过应用上限4000 Token，请缩短后重试", exception.getMessage());
        verify(conversationService, never()).startTurn(any(StartConversationTurnBO.class));
        verify(appRuntime, never()).execute(any());
    }

    @Test
    void shouldLeaveConversationIdBlankForFirstMessage() {
        ChatRequest request = ChatRequest.builder()
                .clientMessageId("client-001")
                .query("你好")
                .applicationCode("support")
                .build();
        ApplicationExecutionGate gate = prepareApplicationExecution("应用回答");

        service.start(request, LOGIN_USER);
        gate.awaitStarted();

        ArgumentCaptor<StartConversationTurnBO> inputCaptor = ArgumentCaptor.forClass(StartConversationTurnBO.class);
        verify(conversationService).startTurn(inputCaptor.capture());
        assertEquals(null, inputCaptor.getValue().getConversationId());
        gate.release();
    }

    @Test
    void shouldNotStartModelAgainWhenClientMessageAlreadyExists() {
        when(conversationService.startTurn(any(StartConversationTurnBO.class)))
                .thenReturn(new ConversationTurnStartBO(new ChatTurnBO(10L, 11L, 12L, 3), false));

        service.start(createChatRequest(), LOGIN_USER);

        verify(aiTracingService, never()).start(any(), any(), any());
        verify(appRuntime, never()).execute(any());
    }

    @Test
    void shouldResolveOnlyOriginalVersionIdentityForRetriedMessage() {
        when(conversationService.startTurn(any(StartConversationTurnBO.class)))
                .thenReturn(new ConversationTurnStartBO(new ChatTurnBO(10L, 11L, 12L, 3), false));
        ConversationMessageEntity originalMessage = assistant(MessageStatus.COMPLETED, "原回答");
        originalMessage.setApplicationVersionId(29L);
        when(messageRepo.findById(12L)).thenReturn(Optional.of(originalMessage));
        when(appVersionService.resolveAssistantIdentity(20L, 29L))
                .thenReturn(new ApplicationAssistantIdentityVO("原版本助手", null));

        ChatStartVO result = service.start(createChatRequest(), LOGIN_USER);

        assertEquals("原版本助手", result.assistant().name());
        verify(appVersionService).resolveAssistantIdentity(20L, 29L);
        verify(appVersionService, never()).resolveAssistantIdentity(20L, 30L);
        verify(appRuntime, never()).execute(any());
    }

    @Test
    void shouldFailPlaceholderWhenAssistantIdentityCannotBeResolved() {
        IllegalStateException failure = new IllegalStateException("storage unavailable");
        when(appVersionService.resolveAssistantIdentity(20L, 30L)).thenThrow(failure);

        assertEquals(failure, assertThrows(IllegalStateException.class,
                () -> service.start(createChatRequest(), LOGIN_USER)));

        verify(conversationService).failAssistant(12L, "", "GENERATION_START_ERROR", "模型生成任务启动失败", TokenUsage.empty());
        verify(appRuntime, never()).execute(any());
        verify(aiTracingService, never()).start(any(), any(), any());
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
        verify(eventPublisher).completeAssistant(
                new ChatTurnBO(10L, 11L, 12L, 3), 30L, "完整回答", TokenUsage.of(12, 4, 16), 99L);
    }

    @Test
    void shouldKeepCompletedResultWhenTransactionAckFailsAfterDatabaseCommit() {
        ApplicationExecutionGate gate = prepareApplicationExecution("完整回答");
        doThrow(new IllegalStateException("事务回执异常"))
                .when(eventPublisher)
                .completeAssistant(any(), any(), any(), any(), any());
        when(conversationService.findAssistantCompletionStatus(10L, 12L, 30L))
                .thenReturn(Optional.of(MessageStatus.COMPLETED));

        service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();
        CompletableFuture<List<ChatGenerationEventBO>> eventsFuture =
                service.resume(12L, 0, 7L).collectList().toFuture();
        gate.release();

        List<ChatGenerationEventBO> events = eventsFuture
                .orTimeout(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)
                .join();

        assertEquals(List.of(
                ChatGenerationEventBO.chunk(1, "完整回答"),
                ChatGenerationEventBO.completed()), events);
        verify(conversationService, never()).failAssistant(anyLong(), any(), any(), any(), any());
        verify(conversationService).findAssistantCompletionStatus(10L, 12L, 30L);
    }

    @Test
    void shouldPublishCancelledWhenConcurrentCancellationWinsCompletion() {
        ApplicationExecutionGate gate = prepareApplicationExecution("完整回答");
        doThrow(new IllegalStateException("事务回执异常"))
                .when(eventPublisher)
                .completeAssistant(any(), any(), any(), any(), any());
        when(conversationService.findAssistantCompletionStatus(10L, 12L, 30L))
                .thenReturn(Optional.of(MessageStatus.CANCELLED));

        service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();
        CompletableFuture<List<ChatGenerationEventBO>> eventsFuture = service.resume(12L, 0, 7L).collectList().toFuture();
        gate.release();

        assertEquals(List.of(ChatGenerationEventBO.chunk(1, "完整回答"), ChatGenerationEventBO.cancelled()),
                eventsFuture.orTimeout(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS).join());
        verify(conversationService, never()).failAssistant(anyLong(), any(), any(), any(), any());
        verify(conversationService).findAssistantCompletionStatus(10L, 12L, 30L);
    }

    @Test
    void shouldFailAndReleaseTaskWhenCompletionStatusCannotBeRead() {
        ApplicationExecutionGate gate = prepareApplicationExecution("完整回答");
        IllegalStateException completionFailure = new IllegalStateException("提交回执未知");
        IllegalStateException statusFailure = new IllegalStateException("状态查询失败");
        doThrow(completionFailure).when(eventPublisher).completeAssistant(any(), any(), any(), any(), any());
        when(conversationService.findAssistantCompletionStatus(10L, 12L, 30L)).thenThrow(statusFailure);

        service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();
        CompletableFuture<List<ChatGenerationEventBO>> events = service.resume(12L, 0, 7L).collectList().toFuture();
        gate.release();

        assertEquals(List.of(ChatGenerationEventBO.chunk(1, "完整回答"), ChatGenerationEventBO.failed()),
                events.orTimeout(2, TimeUnit.SECONDS).join());
        verify(conversationService).findAssistantCompletionStatus(10L, 12L, 30L);
        verify(conversationService).failAssistant(12L, "完整回答", "TERMINAL_PERSIST_ERROR", "回答终态保存失败，请稍后重试", TokenUsage.of(12, 4, 16));
        verify(generationTaskRegistry, org.mockito.Mockito.timeout(2000)).unregister(12L);
        assertEquals(List.of(statusFailure), List.of(completionFailure.getSuppressed()));
    }

    @Test
    void shouldPublishAgentLoopLimitAsActionableFailure() {
        ApplicationExecutionGate gate = new ApplicationExecutionGate();
        doAnswer(invocation -> {
            gate.markStarted();
            gate.awaitRelease();
            throw new RuntimeException(new AgentRuntimeLimitException(
                    "AGENT_TOOL_CALL_LIMIT", "工具调用次数已达到上限，现有信息不足以继续处理"));
        }).when(appRuntime).execute(any(AppExecutionRequest.class));

        service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();
        CompletableFuture<List<ChatGenerationEventBO>> eventsFuture = service.resume(12L, 0, 7L).collectList().toFuture();
        gate.release();

        List<ChatGenerationEventBO> events = eventsFuture
                .orTimeout(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)
                .join();

        assertEquals(List.of(ChatGenerationEventBO.failed(
                "AGENT_TOOL_CALL_LIMIT", "本轮处理步骤已达到上限，请缩小问题范围后重试")), events);
        verify(conversationService).failAssistant(12L, "", "AGENT_TOOL_CALL_LIMIT",
                "本轮处理步骤已达到上限，请缩小问题范围后重试", TokenUsage.empty());
    }

    @Test
    void shouldPublishModelTimeoutAsRetryableFailure() {
        ApplicationExecutionGate gate = new ApplicationExecutionGate();
        doAnswer(invocation -> {
            gate.markStarted();
            gate.awaitRelease();
            throw new IllegalStateException(new TimeoutException("model timeout"));
        }).when(appRuntime).execute(any(AppExecutionRequest.class));

        service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();
        CompletableFuture<List<ChatGenerationEventBO>> eventsFuture = service.resume(12L, 0, 7L).collectList().toFuture();
        gate.release();

        List<ChatGenerationEventBO> events = eventsFuture
                .orTimeout(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)
                .join();

        assertEquals(List.of(ChatGenerationEventBO.failed("AI_MODEL_TIMEOUT", "本次处理等待时间过长，请重试")), events);
        verify(conversationService).failAssistant(
                12L, "", "AI_MODEL_TIMEOUT", "本次处理等待时间过长，请重试", TokenUsage.empty());
    }

    @Test
    void shouldPersistWaitingStateBeforePublishingApprovalEvent() {
        ApplicationExecutionGate gate = new ApplicationExecutionGate();
        InteractionSessionEntity interaction = pendingApproval();
        when(interactionRepo.findById(91L)).thenReturn(Optional.of(interaction));
        doAnswer(invocation -> {
            gate.markStarted();
            gate.awaitRelease();
            return ApplicationChatResult.interrupted(TokenUsage.empty(), RuntimeInterruption.builder()
                    .interactionId(91L)
                    .title("确认执行“发送邮件”")
                    .summary("需要确认")
                    .riskLevel("HIGH")
                    .resourceScope("邮箱")
                    .fingerprint("fingerprint")
                    .revision(0)
                    .build());
        }).when(appRuntime).execute(any(AppExecutionRequest.class));

        service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();
        CompletableFuture<List<ChatGenerationEventBO>> eventsFuture =
                service.resume(12L, 0, 7L).collectList().toFuture();
        gate.release();

        List<ChatGenerationEventBO> events = eventsFuture
                .orTimeout(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)
                .join();

        assertEquals(1, events.size());
        assertEquals(ChatGenerationEventType.INTERACTION_REQUIRED, events.getFirst().type());
        assertEquals("确认执行“发送邮件”", events.getFirst().interaction().getTitle());
        InOrder order = inOrder(conversationService, generationEventRepo);
        order.verify(conversationService).waitForHuman(12L, "", TokenUsage.empty());
        order.verify(generationEventRepo).append(eq(12L), any(ChatGenerationEventBO.class));
    }

    @Test
    void shouldFailWhenModelStopsRespondingAfterUsefulOutput() {
        ApplicationExecutionGate gate = new ApplicationExecutionGate();
        doAnswer(invocation -> {
            AppExecutionRequest request = invocation.getArgument(0);
            gate.markStarted();
            gate.awaitRelease();
            request.getEventPublisher().publishText(new ChatStreamChunk("已经整理出的可用内容"));
            throw new java.util.concurrent.TimeoutException("response idle timeout");
        }).when(appRuntime).execute(any(AppExecutionRequest.class));

        service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();
        CompletableFuture<List<ChatGenerationEventBO>> eventsFuture = service.resume(12L, 0, 7L).collectList().toFuture();
        gate.release();

        List<ChatGenerationEventBO> events = eventsFuture
                .orTimeout(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS)
                .join();

        assertEquals(List.of(
                ChatGenerationEventBO.chunk(1, "已经整理出的可用内容"),
                ChatGenerationEventBO.failed("AI_MODEL_TIMEOUT", "本次处理等待时间过长，请重试")), events);
        verify(conversationService).failAssistant(
                12L, "已经整理出的可用内容", "AI_MODEL_TIMEOUT", "本次处理等待时间过长，请重试", TokenUsage.empty());
    }

    @Test
    void shouldRestorePersistedAgentLoopLimit() {
        ConversationMessageEntity failed = assistant(MessageStatus.FAILED, "");
        failed.setErrorCode("AGENT_TOOL_CALL_LIMIT");
        failed.setErrorMessage("工具调用次数已达到上限，现有信息不足以继续处理");
        when(messageRepo.findOwnedAssistantMessage(12L, 7L)).thenReturn(Optional.of(failed));

        List<ChatGenerationEventBO> events = service.resume(12L, 0, 7L).collectList().block();

        assertEquals(List.of(ChatGenerationEventBO.failed(
                "AGENT_TOOL_CALL_LIMIT", "工具调用次数已达到上限，现有信息不足以继续处理")), events);
    }

    @Test
    void shouldCancelOnlyOnExplicitRequest() {
        ApplicationExecutionGate gate = prepareApplicationExecution("不会返回");
        when(conversationService.cancelAssistant(12L, "", TokenUsage.empty())).thenReturn(true);

        service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();
        service.cancel(12L, 7L);

        verify(conversationService).cancelAssistant(
                12L,
                "",
                TokenUsage.empty());
        verify(generationTaskRegistry).requestCancellation(12L);
        gate.release();
    }

    @Test
    void shouldStopLocalSubscriptionWhenOtherInstancePublishesCancellation() {
        ApplicationExecutionGate gate = prepareApplicationExecution("不会返回");

        service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();
        ArgumentCaptor<Runnable> cancellationHandler = ArgumentCaptor.forClass(Runnable.class);
        verify(generationTaskRegistry).register(eq(12L), cancellationHandler.capture(), any(Runnable.class));

        CompletableFuture<List<ChatGenerationEventBO>> eventsFuture = service.resume(12L, 0, 7L).collectList().toFuture();
        cancellationHandler.getValue().run();

        assertEquals(List.of(ChatGenerationEventBO.cancelled()), eventsFuture
                .orTimeout(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS).join());
        verify(conversationService, never()).cancelAssistant(anyLong(), any(), any());
        gate.release();
    }

    @Test
    void shouldDetachWithoutChangingDatabaseWhenRedisOwnershipIsLost() {
        ApplicationExecutionGate gate = prepareApplicationExecution("不会返回");

        service.start(createChatRequest(), LOGIN_USER);
        gate.awaitStarted();
        ArgumentCaptor<Runnable> ownershipLostHandler = ArgumentCaptor.forClass(Runnable.class);
        verify(generationTaskRegistry).register(eq(12L), any(Runnable.class), ownershipLostHandler.capture());
        CompletableFuture<List<ChatGenerationEventBO>> eventsFuture = service.resume(12L, 0, 7L)
                .collectList().toFuture();

        ownershipLostHandler.getValue().run();

        assertEquals(List.of(), eventsFuture.orTimeout(Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS).join());
        verify(conversationService, never()).cancelAssistant(anyLong(), any(), any());
        verify(conversationService, never()).interruptAssistant(anyLong(), any(), any());
        verify(conversationService, never()).failAssistant(anyLong(), any(), any(), any(), any());
        verify(traceRun).cancel("TASK_OWNERSHIP_LOST");
        gate.release();
    }

    @Test
    void shouldNotStartDuplicateExecutionWhenAnotherInstanceOwnsTask() {
        when(generationTaskRegistry.register(anyLong(), any(Runnable.class), any(Runnable.class)))
                .thenReturn(GenerationTaskRegistry.RegistrationResult.ACTIVE_ELSEWHERE);

        ChatStartVO result = service.start(createChatRequest(), LOGIN_USER);

        assertEquals("12", result.assistantMessageId());
        verify(appRuntime, never()).execute(any(AppExecutionRequest.class));
        verify(conversationService, never()).failAssistant(anyLong(), any(), any(), any(), any());
        verify(traceRun).cancel("TASK_OWNED_BY_ANOTHER_INSTANCE");
    }

    @Test
    void shouldWaitForRemoteInstanceTerminalInsteadOfInterruptingGeneratingMessage() {
        ConversationMessageEntity completed = assistant(MessageStatus.COMPLETED, "远端完整回答");
        when(messageRepo.findById(12L)).thenReturn(Optional.of(completed));

        List<ChatGenerationEventBO> events = service.resume(12L, 0, 7L)
                .collectList()
                .block(Duration.ofSeconds(2));

        assertEquals(List.of(
                ChatGenerationEventBO.snapshot("远端完整回答"),
                ChatGenerationEventBO.completed()), events);
        verify(conversationService, never()).interruptAssistant(anyLong(), any(), any());
    }

    @Test
    void shouldNotInterruptStaleCandidateWhenAnotherInstanceHasActiveLease() {
        ConversationMessageEntity stale = assistant(MessageStatus.GENERATING, "部分回答");
        when(messageRepo.findStaleGenerating(any(), anyInt())).thenReturn(List.of(stale));
        when(generationTaskRegistry.isActive(12L)).thenReturn(true);

        int repaired = service.reconcileStaleGenerations(OffsetDateTime.now(), 10);

        assertEquals(0, repaired);
        verify(conversationService, never()).interruptAssistant(anyLong(), any(), any());
    }

    @Test
    void shouldCancelHitlStateTogetherWhenAssistantIsWaitingForHuman() {
        when(messageRepo.findOwnedAssistantMessage(12L, 7L))
                .thenReturn(Optional.of(assistant(MessageStatus.WAITING_HUMAN, "等待确认")));

        service.cancel(12L, 7L);

        verify(hitlDecisionTxService).cancel(12L, 7L);
        verify(conversationService, never()).cancelWaiting(anyLong(), any(), any());
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
    void shouldKeepSubscriptionOpenAfterApprovalBeforeResumeConsumerStarts() {
        java.util.concurrent.atomic.AtomicReference<ConversationMessageEntity> current =
                new java.util.concurrent.atomic.AtomicReference<>(assistant(MessageStatus.GENERATING, ""));
        when(messageRepo.findOwnedAssistantMessage(12L, 7L)).thenReturn(Optional.of(current.get()));
        when(messageRepo.findById(12L)).thenAnswer(ignored -> Optional.of(current.get()));

        CompletableFuture<List<ChatGenerationEventBO>> subscribed = service.resume(12L, 15L, 7L).collectList().toFuture();

        org.junit.jupiter.api.Assertions.assertFalse(subscribed.isDone());
        current.set(assistant(MessageStatus.COMPLETED, "审批后返回的最终结果"));
        List<ChatGenerationEventBO> events = subscribed.orTimeout(2, TimeUnit.SECONDS).join();
        assertEquals(List.of(ChatGenerationEventBO.snapshot("审批后返回的最终结果"), ChatGenerationEventBO.completed()), events);
        verify(conversationService, never()).interruptAssistant(anyLong(), any(), any());
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
            AppExecutionRequest request = invocation.getArgument(0);
            assertEquals(APPLICATION_SNAPSHOT, request.getApplication());
            assertEquals("你好", request.getQuery());
            assertEquals(LOGIN_USER, request.getLoginUser());
            assertEquals(new ChatTurnBO(10L, 11L, 12L, 3), request.getTurn());
            gate.markStarted();
            gate.awaitRelease();
            ChatExecutionEventPublisher eventPublisher = request.getEventPublisher();
            eventPublisher.publishText(new ChatStreamChunk(answer, TokenUsage.of(12, 4, 16)));
            return ApplicationChatResult.completed(
                    answer, TokenUsage.of(12, 4, 16), List.of(), 99L);
        }).when(appRuntime).execute(any(AppExecutionRequest.class));
        return gate;
    }

    private static ConversationMessageEntity assistant(MessageStatus status, String content) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setId(12L);
        entity.setRole(MessageRole.ASSISTANT);
        entity.setStatus(status);
        entity.setContent(content);
        entity.setApplicationId(20L);
        entity.setApplicationVersionId(30L);
        return entity;
    }

    private static InteractionSessionEntity pendingApproval() {
        InteractionSessionContext context = InteractionSessionContext.builder()
                .title("确认执行“发送邮件”")
                .summary("该操作需要你的明确批准，确认前不会执行。")
                .riskLevel("HIGH")
                .resourceScope("外部邮箱")
                .toolCode("send_mail")
                .build();
        InteractionSessionEntity interaction = new InteractionSessionEntity();
        interaction.setId(91L);
        interaction.setAssistantMessageId(12L);
        interaction.setInteractionType(InteractionSessionType.ACTION_APPROVAL);
        interaction.setStatus(InteractionSessionStatus.PENDING);
        interaction.setRevision(0);
        interaction.setApprovalFingerprint("fingerprint");
        interaction.setContext(context);
        interaction.setExpiresAt(OffsetDateTime.now().plusMinutes(10));
        return interaction;
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
