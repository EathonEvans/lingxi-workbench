package com.lingxi.workbench.chat.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionCheckpointStatus;
import com.lingxi.workbench.application.runtime.model.AppExecutionRequest;
import com.lingxi.workbench.application.runtime.model.ApplicationChatResult;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.vo.ApplicationAssistantIdentityVO;
import com.lingxi.workbench.application.runtime.model.ExecutionResumeContext;
import com.lingxi.workbench.application.runtime.model.ExecutionFailure;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionCheckpointEntity;
import com.lingxi.workbench.application.runtime.model.entity.InteractionSessionEntity;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionStatus;
import com.lingxi.workbench.application.runtime.repo.ExecutionCheckpointRepo;
import com.lingxi.workbench.application.runtime.repo.InteractionSessionRepo;
import com.lingxi.workbench.application.runtime.service.app.ApplicationVersionService;
import com.lingxi.workbench.application.runtime.service.app.AppRuntime;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionFailureClassifier;
import com.lingxi.workbench.application.runtime.service.interaction.HitlDecisionTxService;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.event.ConversationEventPublisher;
import com.lingxi.workbench.chat.holder.RunningGenerationTask;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatInteractionEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ConversationTurnStartBO;
import com.lingxi.workbench.chat.model.bo.StartConversationTurnBO;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.model.request.ChatRequest;
import com.lingxi.workbench.chat.model.vo.ChatStartVO;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.repo.ChatGenerationEventRepo;
import com.lingxi.workbench.chat.service.ChatStreamService;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.chat.service.GenerationTaskRegistry;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.auth.model.bo.UserBO;
import com.lingxi.workbench.auth.repo.UserRepo;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption;
import com.lingxi.workbench.observability.common.enums.AiObservationStage;
import com.lingxi.workbench.observability.service.StageObserver;
import com.lingxi.workbench.observability.service.TraceRun;
import com.lingxi.workbench.observability.service.TracingService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.time.OffsetDateTime;

/**
 * 流式对话服务实现。
 *
 * <p>模型生成任务由服务端主动订阅并独立运行，SSE 连接只是任务观察者。
 * 浏览器断开连接不会取消模型调用，只有显式取消请求才会终止任务。</p>
 *
 * <p>一次完整调用按照“配置加载 → 输入预算预检 → 消息占位落库 → 共享上下文构建 → 后台模型调用 →
 * 增量事件推送 → Assistant 消息终态落库”的顺序执行。数据库中的 Assistant
 * 消息是生成任务的持久化标识，进程内任务只负责保存实时增量和订阅状态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamServiceImpl implements ChatStreamService {

    private static final String MODEL_ERROR_CODE = "AI_MODEL_ERROR";
    private static final String TASK_START_ERROR_CODE = "GENERATION_START_ERROR";
    private static final String TASK_START_ERROR_MESSAGE = "模型生成任务启动失败";
    private static final Duration REMOTE_RESUME_POLL_INTERVAL = Duration.ofMillis(500);
    // 运行中对话任务
    private final Map<Long, RunningGenerationTask> runningTasks = new ConcurrentHashMap<>();

    private final ConversationService conversationService;
    private final ConversationMessageRepo messageRepo;
    private final ChatGenerationEventRepo generationEventRepo;
    private final ApplicationVersionService appVersionService;
    private final AppRuntime appRuntime;
    private final ConversationEventPublisher eventPublisher;
    private final TokenCountEstimator tokenCountEstimator;
    private final TracingService aiTracingService;
    private final StageObserver aiStageObserver;
    private final ExecutionFailureClassifier failureClassifier;
    private final InteractionSessionRepo interactionRepo;
    private final ExecutionCheckpointRepo checkpointRepo;
    private final UserRepo userRepo;
    private final HitlDecisionTxService hitlDecisionTxService;
    private final GenerationTaskRegistry generationTaskRegistry;

    /**
     * 创建并启动一次流式模型生成任务。
     *
     * <p>本方法返回时，用户消息和 Assistant 占位消息已经落库，模型生成任务也已由服务端主动订阅。
     * 返回的 {@link Flux} 仅用于观察生成事件，客户端取消订阅不会反向取消模型任务。</p>
     *
     * @param request   用户输入、会话标识和客户端幂等消息标识
     * @param loginUser 当前登录用户
     * @return 已落库的会话、用户消息和 Assistant 消息主键
     */
    @Override
    public ChatStartVO start(ChatRequest request, LoginUser loginUser) {
        String query = request.getQuery();
        Long userId = loginUser.id();

        // 读取当前锁定的应用运行时快照
        AppRuntimeSnapshot applicationSnapshot = appVersionService.loadRuntimeAppConfig(request.getApplicationCode().trim().toLowerCase());
        // 校验输入token上限
        this.validateInputBudget(query, applicationSnapshot);

        // 观测执行，消息占位，一轮会话（用户 + 模型回复（占位））
        StartConversationTurnBO turnInput = StartConversationTurnBO.builder()
                .userId(userId)
                .conversationId(request.getConversationId())
                .clientMessageId(request.getClientMessageId())
                .prompt(query)
                .applicationId(applicationSnapshot.applicationId())
                .applicationVersionId(applicationSnapshot.versionId())
                .build();
        // 消息落库
        ConversationTurnStartBO turnStart = aiStageObserver.observe(AiObservationStage.MESSAGE_PERSIST, () -> conversationService.startTurn(turnInput));

        ChatTurnBO turn = turnStart.turn();
        // 相同 clientMessageId 的网络重试只返回原任务标识，不得重复启动模型和重复扣费。
        if (!turnStart.isCreated()) {
            ConversationMessageEntity assistantMessage = messageRepo.findById(turn.assistantMessageId())
                    .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "Assistant消息不存在"));
            ApplicationAssistantIdentityVO assistantIdentity = appVersionService.resolveAssistantIdentity(
                    assistantMessage.getApplicationId(), assistantMessage.getApplicationVersionId());
            return toStartVO(turn, assistantIdentity);
        }

        try {
            ApplicationAssistantIdentityVO assistantIdentity = appVersionService
                    .resolveAssistantIdentity(applicationSnapshot.applicationId(), applicationSnapshot.versionId());
            // Assistant 消息主键同时作为生成任务主键，便于 SSE 续接、取消和数据库终态恢复使用同一标识。
            TraceRun traceRun = aiTracingService.start(applicationSnapshot, turn, userId);
            RunningGenerationTask task = RunningGenerationTask.builder()
                    .turn(turn)
                    .userId(userId)
                    .applicationVersionId(applicationSnapshot.versionId())
                    .memoryConfig(applicationSnapshot.definition().memoryConf())
                    .eventRepo(generationEventRepo)
                    .traceRun(traceRun)
                    .build();

            if (!registerTask(task)) {
                task.traceRun().cancel("TASK_OWNED_BY_ANOTHER_INSTANCE");
                return toStartVO(turn, assistantIdentity);
            }

            // 构建执行参数
            AppExecutionRequest executionRequest = AppExecutionRequest.builder()
                    .application(applicationSnapshot)
                    .query(query)
                    .loginUser(loginUser)
                    .turn(turn)
                    .clarificationResponse(request.getClarificationResponse())
                    .explicitSkillCodes(request.getSkillCodes())
                    .eventPublisher(task)
                    .build();
            if (!task.isTerminal()) {
                this.startGeneration(executionRequest);
            }
            return toStartVO(turn, assistantIdentity);
        } catch (RuntimeException exception) {
            // 消息占位后的身份解析、任务装配和启动失败都必须推进失败终态。
            this.markTaskStartFailed(exception, turn);
            throw exception;
        }
    }

    /**
     * 将任务置于失败状态
     *
     * @param exception 异常
     * @param turn      单轮对话
     */
    private void markTaskStartFailed(RuntimeException exception, ChatTurnBO turn) {
        runningTasks.remove(turn.assistantMessageId());
        generationTaskRegistry.unregister(turn.assistantMessageId());
        try {
            // Assistant 占位消息已经落库，启动失败时必须将其从 GENERATING 推进到 FAILED，避免形成僵尸任务。
            conversationService.failAssistant(turn.assistantMessageId(), "", TASK_START_ERROR_CODE, TASK_START_ERROR_MESSAGE, TokenUsage.empty());
        } catch (RuntimeException persistenceException) {
            exception.addSuppressed(persistenceException);
            log.error("模型生成任务启动失败后更新消息状态失败，assistantMessageId={}", turn.assistantMessageId(), persistenceException);
        }
    }

    /**
     * 消息落库前执行输入准入校验，避免为无法执行的请求创建失败占位消息。
     */
    private void validateInputBudget(String query, AppRuntimeSnapshot application) {
        int actualTokens = tokenCountEstimator.estimate(query);
        int maxTokens = application.definition().contextPolicy().budget().getMaxInputTokens();
        if (actualTokens > maxTokens) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "当前输入约" + actualTokens + " Token，超过应用上限" + maxTokens + " Token，请缩短后重试");
        }
    }

    /**
     * 将落库的对话轮次转换为前端启动响应。
     *
     * <p>主键统一转为字符串返回，避免 JavaScript Number 对大整数产生精度损失。</p>
     *
     * @param turn 已落库的对话轮次（包含会话、用户消息和 Assistant 消息主键）
     * @return 前端用于订阅 SSE 的启动响应
     */
    private ChatStartVO toStartVO(ChatTurnBO turn, ApplicationAssistantIdentityVO assistantIdentity) {
        return new ChatStartVO(
                String.valueOf(turn.conversationId()),
                String.valueOf(turn.userMessageId()),
                String.valueOf(turn.assistantMessageId()),
                assistantIdentity);
    }

    @Override
    public Flux<ChatGenerationEventBO> resume(Long assistantMessageId, long afterSequence, Long userId) {
        ConversationMessageEntity message = requireOwnedAssistantMessage(assistantMessageId, userId);
        RunningGenerationTask task = runningTasks.get(assistantMessageId);
        if (Objects.nonNull(task)) {
            return task.eventsAfter(afterSequence);
        }
        // 非源实例只能从 Redis 增量缓存续接，不能把仍在其他实例运行的任务误标为中断。
        List<ChatGenerationEventBO> cachedEvents = generationEventRepo.findAfter(assistantMessageId, afterSequence);
        if (message.getStatus() == MessageStatus.GENERATING) {
            return Flux.fromIterable(cachedEvents)
                    .concatWith(awaitRemoteGeneration(assistantMessageId, afterSequence, cachedEvents));
        }
        return Flux.fromIterable(cachedEvents).concatWith(restoreTerminalEvents(message));
    }

    @Override
    public void cancel(Long assistantMessageId, Long userId) {
        ConversationMessageEntity message = requireOwnedAssistantMessage(assistantMessageId, userId);
        if (message.getStatus() == MessageStatus.GENERATING) {
            RunningGenerationTask task = runningTasks.get(assistantMessageId);
            if (Objects.nonNull(task)) {
                cancelTask(task);
                return;
            }
            if (conversationService.cancelAssistant(assistantMessageId, message.getContent(), TokenUsage.empty())) {
                generationTaskRegistry.requestCancellation(assistantMessageId);
            }
        } else if (message.getStatus() == MessageStatus.WAITING_HUMAN) {
            hitlDecisionTxService.cancel(assistantMessageId, userId);
        }
    }

    @Override
    public void resumeInteraction(Long interactionId) {
        InteractionSessionEntity interaction = interactionRepo.findById(interactionId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "HITL交互不存在"));
        if (interaction.getStatus() != InteractionSessionStatus.RESOLVED) {
            return;
        }
        ExecutionCheckpointEntity checkpoint = checkpointRepo.findById(interaction.getCheckpointId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "HITL检查点不存在"));
        if (checkpoint.getStatus() != ExecutionCheckpointStatus.ACTIVE
                || checkpoint.getCheckpointVersion() == null
                || checkpoint.getCheckpointVersion() != AgentScopeCheckpoint.CURRENT_VERSION
                || checkpoint.getPayload() == null
                || !Objects.equals(checkpoint.getChecksum(), interaction.getApprovalFingerprint())
                || !Objects.equals(checkpoint.getPayload().getFingerprint(), interaction.getApprovalFingerprint())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "HITL检查点版本、状态或指纹不合法");
        }
        ConversationMessageEntity assistant = messageRepo.findById(interaction.getAssistantMessageId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Assistant消息不存在"));
        if (assistant.getStatus() == MessageStatus.COMPLETED || assistant.getStatus() == MessageStatus.CANCELLED
                || assistant.getStatus() == MessageStatus.FAILED || assistant.getStatus() == MessageStatus.INTERRUPTED) {
            return;
        }
        if (assistant.getStatus() == MessageStatus.WAITING_HUMAN) {
            conversationService.resumeFromHuman(assistant.getId());
        } else if (runningTasks.containsKey(assistant.getId())) {
            return;
        }
        ConversationMessageEntity userMessage = messageRepo.findBySequence(
                        assistant.getConversationId(), assistant.getSequenceNo() - 1)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.CONFLICT, "HITL原始用户消息不存在"));
        ChatTurnBO turn = new ChatTurnBO(assistant.getConversationId(), userMessage.getId(),
                assistant.getId(), userMessage.getSequenceNo());
        AppRuntimeSnapshot application = appVersionService.loadRuntimeAppVersion(
                assistant.getApplicationId(), assistant.getApplicationVersionId());
        LoginUser loginUser = toLoginUser(userRepo.findById(interaction.getUserId())
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "HITL用户不存在")));
        RunningGenerationTask task = RunningGenerationTask.builder()
                .turn(turn)
                .userId(interaction.getUserId())
                .applicationVersionId(application.versionId())
                .memoryConfig(application.definition().memoryConf())
                .eventRepo(generationEventRepo)
                .traceRun(aiTracingService.start(application, turn, interaction.getUserId()))
                .initialAnswer(assistant.getContent())
                .initialUsage(TokenUsage.of(assistant.getInputTokens(), assistant.getOutputTokens(), assistant.getTotalTokens()))
                .initialSequence(generationEventRepo.lastSequence(assistant.getId()))
                .build();
        if (!registerTask(task)) {
            task.traceRun().cancel("TASK_OWNED_BY_ANOTHER_INSTANCE");
            return;
        }
        AgentScopeCheckpoint runtimeCheckpoint = checkpoint.getPayload();
        AppExecutionRequest request = AppExecutionRequest.builder()
                .application(application)
                .query(runtimeCheckpoint.getQuery())
                .loginUser(loginUser)
                .turn(turn)
                .explicitSkillCodes(runtimeCheckpoint.getExplicitSkillCodes())
                .eventPublisher(task)
                .resumeContext(ExecutionResumeContext.builder()
                        .checkpoint(runtimeCheckpoint)
                        .interactionId(interaction.getId())
                        .fingerprint(interaction.getApprovalFingerprint())
                        .build())
                .build();
        if (!task.isTerminal()) {
            startGeneration(request);
        }
    }

    private static LoginUser toLoginUser(UserBO user) {
        return new LoginUser(user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl());
    }

    @Override
    public int reconcileStaleGenerations(OffsetDateTime before, int limit) {
        int repaired = 0;
        for (ConversationMessageEntity message : messageRepo.findStaleGenerating(before, limit)) {
            if (runningTasks.containsKey(message.getId()) || generationTaskRegistry.isActive(message.getId())) {
                continue;
            }
            conversationService.interruptAssistant(message.getId(), message.getContent(), TokenUsage.empty());
            repaired++;
        }
        return repaired;
    }

    /**
     * 应用正常关闭时终止进程内任务，避免数据库长期停留在生成中状态。
     */
    @PreDestroy
    void shutdownRunningTasks() {
        for (RunningGenerationTask task : List.copyOf(runningTasks.values())) {
            try {
                interruptTask(task);
            } catch (RuntimeException exception) {
                log.error("应用关闭时更新模型任务状态失败，assistantMessageId={}", task.id(), exception);
            }
        }
    }

    /**
     * Application 意图路由和目标执行放到有界弹性线程池，避免阻塞 HTTP 请求线程。
     * Agent 或 Workflow 执行期间通过 task 实时发布文本和工具状态，目标执行完成后再校准
     * 最终回答与 Token 用量；任务取消仍会释放该后台订阅。
     */
    private void startGeneration(AppExecutionRequest executionRequest) {
        RunningGenerationTask task = (RunningGenerationTask) executionRequest.getEventPublisher();

        Disposable subscription = Mono
                .fromCallable(() -> executeApplication(task, executionRequest))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(result -> completeApplicationTask(task, result),
                        exception -> failTask(task, exception));
        task.setSubscription(subscription);
    }

    /**
     * 显式取消会中断正在阻塞等待的模型调用。任务已进入终态时，该中断是预期结果，
     * 返回 null 让 Mono 正常结束，避免订阅释放后产生 onErrorDropped。
     */
    private ApplicationChatResult executeApplication(RunningGenerationTask task, AppExecutionRequest executionRequest) {
        try {
            return task.traceRun().scoped(() -> appRuntime.execute(executionRequest));
        } catch (RuntimeException exception) {
            if (task.isTerminal() && hasCause(exception, InterruptedException.class)) {
                return null;
            }
            throw exception;
        }
    }

    /**
     * 目标执行成功后推进任务终态：校准最终回答与 Token 用量，并落库完成事件。
     *
     * <p>结果中的回答与用量是意图路由后目标执行的最终产物，可能覆盖流式过程中
     * 累积的临时文本，因此通过 {@code beginCompletion} 原子地校准回答并取得完成处理权。</p>
     *
     * @param task   运行中的生成任务
     * @param result 目标执行结果（最终回答、Token 用量和模型实例）
     */
    private void completeApplicationTask(RunningGenerationTask task, ApplicationChatResult result) {
        if (result.isInterrupted()) {
            pauseTaskForHuman(task, result.getInterruption());
            return;
        }
        // 校准回答与竞争完成权在同一临界区内，取消后到达的结果不得再改写内容或发布快照。
        if (!task.beginCompletion(result.answer(), result.tokenUsage())) {
            return;
        }
        Long modelInstanceId = result.modelInstanceId();
        try {
            task.traceRun().scoped(() -> aiStageObserver.observe(AiObservationStage.TERMINAL, () -> {
                eventPublisher.completeAssistant(
                        task.turn(), task.applicationVersionId(), task.answer(), task.tokenUsage(), modelInstanceId);
                task.publishTerminal(ChatGenerationEventBO.completed());
            }));
            task.traceRun().complete(task.tokenUsage(), modelInstanceId);
        } catch (Exception exception) {
            Optional<MessageStatus> persistedStatus = findPersistedStatus(task, exception);
            if (persistedStatus.orElse(null) == MessageStatus.COMPLETED) {
                // 本地事务已提交但Broker回执异常时，以PostgreSQL终态为准，避免前端误报失败。
                log.warn("事务消息回执异常但Assistant已完成落库，assistantMessageId={}", task.id());
                task.publishTerminal(ChatGenerationEventBO.completed());
                task.traceRun().complete(task.tokenUsage(), modelInstanceId);
                return;
            }
            if (persistedStatus.orElse(null) == MessageStatus.CANCELLED) {
                // 跨实例取消先经数据库 CAS 确认；当前实例不得把已取消消息错误投影成失败。
                log.info("Assistant已由其他实例取消，忽略完成回执，assistantMessageId={}", task.id());
                task.publishTerminal(ChatGenerationEventBO.cancelled());
                task.traceRun().cancel("USER_CANCELLED");
                return;
            }
            log.error("模型回答完成后持久化失败，assistantMessageId={}", task.id(), exception);
            try {
                // 事务消息未提交时收敛占位消息，避免 GENERATING 僵尸任务；已完成消息不会被反向覆盖。
                conversationService.failAssistant(
                        task.id(), task.answer(), "TERMINAL_PERSIST_ERROR", "回答终态保存失败，请稍后重试", task.tokenUsage());
            } catch (RuntimeException persistenceException) {
                exception.addSuppressed(persistenceException);
                log.error("回答终态失败后更新消息状态失败，assistantMessageId={}", task.id(), persistenceException);
            }
            task.publishTerminal(ChatGenerationEventBO.failed());
            task.traceRun().fail(exception);
        } finally {
            removeTask(task);
        }
    }

    /**
     * 模型流异常结束：保留已生成的部分回答，记录统一错误信息并发布失败事件。
     */
    private void failTask(RunningGenerationTask task, Throwable exception) {
        if (!task.beginTerminal()) {
            return;
        }
        ExecutionFailure failure = failureClassifier.classify(exception);
        try {
            conversationService.failAssistant(task.id(), task.answer(), failure.code(), failure.clientMessage(), task.tokenUsage());
        } catch (RuntimeException persistenceException) {
            log.error("模型调用失败后更新消息状态失败，assistantMessageId={}", task.id(), persistenceException);
        } finally {
            if (findPersistedStatus(task, exception).orElse(null) == MessageStatus.CANCELLED) {
                task.publishTerminal(ChatGenerationEventBO.cancelled());
                task.traceRun().cancel("USER_CANCELLED");
            } else {
                task.publishTerminal(ChatGenerationEventBO.failed(failure.code(), failure.clientMessage()));
                task.traceRun().fail(exception);
                log.error("模型流式对话调用失败，assistantMessageId={}", task.id(), exception);
            }
            removeTask(task);
        }
    }

    /**
     * HITL 门禁已持久化检查点后释放模型线程，保留同一 Assistant 消息等待异步恢复。
     */
    private void pauseTaskForHuman(RunningGenerationTask task, RuntimeInterruption interruption) {
        try {
            conversationService.waitForHuman(task.id(), task.answer(), task.tokenUsage());
            if (interruption == null || interruption.getInteractionId() == null) {
                throw new BusinessException(HttpStatusEnum.CONFLICT, "Runtime人工中断事实不完整");
            }
            task.publishInteractionRequired(toInteractionEvent(interruption));
            task.pauseForHuman();
            task.traceRun().cancel("WAITING_HUMAN");
            log.info("模型任务等待人工决策，assistantMessageId={}", task.id());
        } finally {
            removeTask(task);
        }
    }

    private static ChatInteractionEventBO toInteractionEvent(RuntimeInterruption interruption) {
        return ChatInteractionEventBO.builder()
                .interactionId(String.valueOf(interruption.getInteractionId()))
                .interactionType("ACTION_APPROVAL")
                .title(interruption.getTitle())
                .summary(interruption.getSummary())
                .riskLevel(interruption.getRiskLevel())
                .resourceScope(interruption.getResourceScope())
                .fingerprint(interruption.getFingerprint())
                .revision(interruption.getRevision())
                .expiresAt(interruption.getExpiresAt())
                .build();
    }

    /**
     * 事务消息发送结果不确定时，以PostgreSQL中的Assistant终态作为用户侧结果依据。
     */
    private Optional<MessageStatus> findPersistedStatus(RunningGenerationTask task, Throwable originalException) {
        try {
            return conversationService.findAssistantCompletionStatus(task.turn().conversationId(), task.id(), task.applicationVersionId());
        } catch (RuntimeException statusException) {
            if (Objects.nonNull(originalException)) {
                originalException.addSuppressed(statusException);
            }
            log.warn("Assistant终态复核失败，assistantMessageId={}", task.id(), statusException);
            return Optional.empty();
        }
    }

    /**
     * 沿异常链查找是否存在指定类型的根因。
     *
     * <p>用于区分"任务已被取消/终止后模型调用被中断"这一预期异常与其他真实失败，
     * 避免把用户主动取消误判为系统错误。</p>
     *
     * @param exception 待检查的异常
     * @param causeType 需要查找的根因类型
     * @return 异常链中存在该类型根因时返回 true
     */
    private static boolean hasCause(Throwable exception, Class<? extends Throwable> causeType) {
        Throwable current = exception;
        while (Objects.nonNull(current)) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 用户主动取消：先由数据库条件更新确认终态，再向持有订阅的实例广播取消信号。
     */
    private void cancelTask(RunningGenerationTask task) {
        if (!conversationService.cancelAssistant(task.id(), task.answer(), task.tokenUsage())) {
            return;
        }
        generationTaskRegistry.requestCancellation(task.id());
        finishCancelledTask(task);
    }

    /**
     * 数据库取消已确认后，本机或远端实例都只释放本机订阅并发送取消事件。
     */
    private void finishCancelledTask(RunningGenerationTask task) {
        if (!task.beginTerminal()) {
            return;
        }
        task.disposeModelSubscription();
        task.publishTerminal(ChatGenerationEventBO.cancelled());
        removeTask(task);
        task.traceRun().cancel("USER_CANCELLED");
    }

    /**
     * 应用关闭导致任务中断：停止模型订阅并把数据库状态推进到 INTERRUPTED。
     */
    private void interruptTask(RunningGenerationTask task) {
        if (!task.beginTerminal()) {
            return;
        }
        task.disposeModelSubscription();
        try {
            conversationService.interruptAssistant(task.id(), task.answer(), task.tokenUsage());
        } finally {
            if (findPersistedStatus(task, null).orElse(null) == MessageStatus.CANCELLED) {
                task.publishTerminal(ChatGenerationEventBO.cancelled());
                task.traceRun().cancel("USER_CANCELLED");
            } else {
                task.publishTerminal(ChatGenerationEventBO.failed());
                task.traceRun().cancel("APPLICATION_SHUTDOWN");
            }
            removeTask(task);
        }
    }

    /**
     * 注册本机任务，跨实例取消到达时只终止本机模型订阅。
     */
    private boolean registerTask(RunningGenerationTask task) {
        if (runningTasks.putIfAbsent(task.id(), task) != null) {
            return false;
        }
        try {
            GenerationTaskRegistry.RegistrationResult registration = generationTaskRegistry.register(
                    task.id(), () -> finishCancelledTask(task), () -> detachTask(task));
            if (registration == GenerationTaskRegistry.RegistrationResult.ACQUIRED) {
                return true;
            }
        } catch (RuntimeException exception) {
            runningTasks.remove(task.id(), task);
            throw exception;
        }
        runningTasks.remove(task.id(), task);
        return false;
    }

    /** Redis租约被其他实例抢占时停止本机重复执行，数据库状态由新所有者继续推进。 */
    private void detachTask(RunningGenerationTask task) {
        if (!task.beginTerminal()) {
            return;
        }
        runningTasks.remove(task.id(), task);
        task.disposeModelSubscription();
        task.detachObservers();
        task.traceRun().cancel("TASK_OWNERSHIP_LOST");
    }

    /**
     * 仅持有当前任务的实例可以注销取消处理器，防止旧任务清理误删新恢复任务。
     */
    private void removeTask(RunningGenerationTask task) {
        if (runningTasks.remove(task.id(), task)) {
            generationTaskRegistry.unregister(task.id());
        }
    }

    /**
     * 续接落在其他实例的任务：轮询短期事件缓存和数据库终态，不修改任何业务状态。
     */
    private Flux<ChatGenerationEventBO> awaitRemoteGeneration(Long assistantMessageId, long afterSequence,
                                                              List<ChatGenerationEventBO> initialEvents) {
        AtomicLong latestSequence = new AtomicLong(Math.max(afterSequence, initialEvents.stream()
                .mapToLong(ChatGenerationEventBO::sequence)
                .max()
                .orElse(afterSequence)));
        return Flux.interval(REMOTE_RESUME_POLL_INTERVAL)
                .map(ignored -> pollRemoteGeneration(assistantMessageId, latestSequence))
                .takeUntil(RemoteGenerationPoll::finished)
                .concatMap(poll -> {
                    Flux<ChatGenerationEventBO> incremental = Flux.fromIterable(poll.events());
                    return poll.finished()
                            ? incremental.concatWith(restoreTerminalEvents(poll.message()))
                            : incremental;
                });
    }

    private RemoteGenerationPoll pollRemoteGeneration(Long assistantMessageId, AtomicLong latestSequence) {
        List<ChatGenerationEventBO> events = generationEventRepo.findAfter(assistantMessageId, latestSequence.get());
        events.stream().mapToLong(ChatGenerationEventBO::sequence).max()
                .ifPresent(sequence -> latestSequence.accumulateAndGet(sequence, Math::max));
        ConversationMessageEntity message = messageRepo.findById(assistantMessageId)
                .orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "Assistant消息不存在"));
        return new RemoteGenerationPoll(events, message, message.getStatus() != MessageStatus.GENERATING);
    }

    /**
     * 当进程内任务已不存在时，根据数据库消息恢复一个可直接结束 SSE 的事件流。
     *
     * <p>若数据库保存了部分或完整文本，先发送 SNAPSHOT；随后根据消息状态发送终态事件。
     * {@code GENERATING} 只能由陈旧任务对账在确认无活跃实例后修正，SSE 续接不得改写。</p>
     */
    private Flux<ChatGenerationEventBO> restoreTerminalEvents(ConversationMessageEntity message) {
        Flux<ChatGenerationEventBO> snapshot = Flux.empty();
        if (CharSequenceUtil.isNotEmpty(message.getContent())) {
            snapshot = Flux.just(ChatGenerationEventBO.snapshot(message.getContent()));
        }

        // 等待人工决策不是失败终态，交互卡片已经持久化为 SSE 事件，恢复时不得伪造失败事件。
        if (message.getStatus() == MessageStatus.WAITING_HUMAN) {
            return snapshot;
        }

        if (message.getStatus() == MessageStatus.GENERATING) {
            throw new IllegalStateException("生成中的Assistant消息必须继续等待远端终态");
        }

        ChatGenerationEventBO terminal = statusToTerminalEvent(message);
        return snapshot.concatWithValues(terminal);
    }

    /**
     * 将消息终态映射为对应的 SSE 终态事件。
     */
    private ChatGenerationEventBO statusToTerminalEvent(ConversationMessageEntity message) {
        return switch (message.getStatus()) {
            case COMPLETED -> ChatGenerationEventBO.completed();
            case CANCELLED -> ChatGenerationEventBO.cancelled();
            case FAILED -> restoredFailureEvent(message);
            case INTERRUPTED, GENERATING -> ChatGenerationEventBO.failed();
            case WAITING_HUMAN -> throw new IllegalStateException("等待人工决策的消息不存在终态事件");
        };
    }

    /**
     * 根据数据库消息的错误信息构造恢复场景下的失败终态事件。
     *
     * <p>原任务所在进程已退出时，SSE 恢复只能读取持久化的错误码和错误消息；
     * 两者缺失时回退到模型服务通用错误码与安全提示文案。</p>
     *
     * @param message 已落库的 Assistant 消息（含错误码与错误消息）
     * @return 携带恢复错误信息的失败终态事件
     */
    private static ChatGenerationEventBO restoredFailureEvent(ConversationMessageEntity message) {
        String code = Objects.requireNonNullElse(message.getErrorCode(), MODEL_ERROR_CODE);
        String errorMessage = Objects.requireNonNullElse(
                message.getErrorMessage(), "模型服务暂时不可用，请稍后重试");
        return ChatGenerationEventBO.failed(code, errorMessage);
    }

    /**
     * @param events   本轮读取到的跨实例增量事件
     * @param message  当前数据库消息快照
     * @param finished 数据库是否已离开生成态
     */
    private record RemoteGenerationPoll(List<ChatGenerationEventBO> events, ConversationMessageEntity message,
                                        boolean finished) {
    }

    /**
     * 查询 Assistant 消息并同时校验消息属于当前用户，防止越权续接或取消他人任务。
     */
    private ConversationMessageEntity requireOwnedAssistantMessage(Long assistantMessageId, Long userId) {
        return messageRepo.findOwnedAssistantMessage(assistantMessageId, userId).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "模型生成任务不存在"));
    }

}
