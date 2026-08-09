package com.lingxi.workbench.chat.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.application.runtime.model.ApplicationChatExecutionRequest;
import com.lingxi.workbench.application.runtime.model.ApplicationChatResult;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.ApplicationRuntimeService;
import com.lingxi.workbench.application.runtime.service.ApplicationChatExecutionService;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.holder.RunningGenerationTask;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.model.request.ChatRequest;
import com.lingxi.workbench.chat.model.vo.ChatStartVO;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.repo.ChatGenerationEventRepo;
import com.lingxi.workbench.chat.service.ChatStreamService;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final String MODEL_ERROR_MESSAGE = "模型服务调用失败";
    private static final String TASK_START_ERROR_CODE = "GENERATION_START_ERROR";
    private static final String TASK_START_ERROR_MESSAGE = "模型生成任务启动失败";
    // 运行中对话任务
    private final Map<Long, RunningGenerationTask> runningTasks = new ConcurrentHashMap<>();

    private final ConversationService conversationService;
    private final ConversationMessageRepo messageRepo;
    private final ChatGenerationEventRepo generationEventRepo;
    private final ApplicationRuntimeService applicationRuntimeService;
    private final ApplicationChatExecutionService applicationChatExecutionService;
    private final ApplicationEventPublisher eventPublisher;
    private final TokenCountEstimator tokenCountEstimator;

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
        String query = request.getQuery().trim();
        Long userId = loginUser.id();

        /*
         * 配置解析必须发生在消息落库之前：
         * 1. 配置无效时不创建 GENERATING 占位消息；
         * 2. Assistant 消息先锁定 ApplicationVersion；实际模型要等意图命中叶子后才能确定。
         */
        AppRuntimeSnapshot applicationSnapshot = applicationRuntimeService.loadRuntimeAppConfig(request.getApplicationCode().trim().toLowerCase());
        validateInputBudget(query, applicationSnapshot);

        /*
         * startTurn 在一个数据库事务中锁定会话并创建两条消息：
         * 已完成的 User 消息，以及等待后台任务填充的 GENERATING Assistant 消息。
         * 消息先落库、模型后启动，才能为启动失败、取消和服务重启保留统一的持久化任务标识。
         */
        ChatTurnBO turn = conversationService.startTurn(userId, Long.valueOf(request.getConversationId()), request.getClientMessageId(), query, null, applicationSnapshot.applicationId(), applicationSnapshot.versionId());

        // Assistant 消息主键同时作为生成任务主键，便于 SSE 续接、取消和数据库终态恢复使用同一标识。
        RunningGenerationTask task = new RunningGenerationTask(turn, userId, applicationSnapshot.versionId(),
                applicationSnapshot.definition().memoryConf(), generationEventRepo);
        try {
            /*
             * Application 是所有对话的运行入口，后台任务必须先按发布快照识别意图，
             * 再执行叶子映射目标，不能绕过路由读取平台默认模型。
             */
            runningTasks.put(turn.assistantMessageId(), task);

            // 构建执行参数
            ApplicationChatExecutionRequest executionRequest = ApplicationChatExecutionRequest.builder()
                    .application(applicationSnapshot)
                    .query(query)
                    .loginUser(loginUser)
                    .turn(turn)
                    .clarificationResponse(request.getClarificationResponse())
                    .eventPublisher(task)
                    .build();
            this.startApplicationGeneration(executionRequest);
        } catch (RuntimeException exception) {
            /*
             * 这里只处理任务注册或 Reactor 订阅创建阶段同步抛出的异常。
             * 路由或目标执行进入后台线程后的异常由 failTask 统一推进 Assistant 失败终态。
             */
            runningTasks.remove(turn.assistantMessageId());
            try {
                // Assistant 占位消息已经落库，启动失败时必须将其从 GENERATING 推进到 FAILED，避免形成僵尸任务。
                conversationService.failAssistant(turn.assistantMessageId(), "", TASK_START_ERROR_CODE, TASK_START_ERROR_MESSAGE, TokenUsage.empty());
            } catch (RuntimeException persistenceException) {
                exception.addSuppressed(persistenceException);
                log.error("模型生成任务启动失败后更新消息状态失败，assistantMessageId={}", turn.assistantMessageId(), persistenceException);
            }
            throw exception;
        }

        // Long 主键转为字符串返回前端，避免 JavaScript Number 对大整数产生精度损失。
        return toStartVO(turn);
    }

    /** 消息落库前执行输入准入校验，避免为无法执行的请求创建失败占位消息。 */
    private void validateInputBudget(String query, AppRuntimeSnapshot application) {
        int actualTokens = tokenCountEstimator.estimate(query);
        int maxTokens = application.definition().contextPolicy().budget().getMaxInputTokens();
        if (actualTokens > maxTokens) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST,
                    "当前输入约" + actualTokens + " Token，超过应用上限" + maxTokens + " Token，请缩短后重试");
        }
    }

    private ChatStartVO toStartVO(ChatTurnBO turn) {
        return new ChatStartVO(String.valueOf(turn.conversationId()), String.valueOf(turn.userMessageId()), String.valueOf(turn.assistantMessageId()));
    }

    @Override
    public Flux<ChatGenerationEventBO> resume(Long assistantMessageId, long afterSequence, Long userId) {
        requireOwnedAssistantMessage(assistantMessageId, userId);
        RunningGenerationTask task = runningTasks.get(assistantMessageId);
        if (Objects.nonNull(task)) {
            return task.eventsAfter(afterSequence);
        }
        // 查询与任务结束可能并发发生，任务已移除时重新读取一次数据库终态。
        List<ChatGenerationEventBO> cachedEvents = generationEventRepo.findAfter(assistantMessageId, afterSequence);
        return Flux.fromIterable(cachedEvents).concatWith(restoreTerminalEvents(requireOwnedAssistantMessage(assistantMessageId, userId)));
    }

    @Override
    public void cancel(Long assistantMessageId, Long userId) {
        ConversationMessageEntity message = requireOwnedAssistantMessage(assistantMessageId, userId);
        RunningGenerationTask task = runningTasks.get(assistantMessageId);
        if (Objects.nonNull(task)) {
            cancelTask(task);
            return;
        }
        if (message.getStatus() == MessageStatus.GENERATING) {
            conversationService.cancelAssistant(assistantMessageId, message.getContent(), TokenUsage.empty());
        }
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
    private void startApplicationGeneration(ApplicationChatExecutionRequest executionRequest) {
        RunningGenerationTask task = (RunningGenerationTask) executionRequest.getEventPublisher();

        Disposable subscription = Mono
                .fromCallable(() -> applicationChatExecutionService.execute(executionRequest))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(result -> completeApplicationTask(task, result),
                        exception -> failTask(task, exception));
        task.setSubscription(subscription);
    }

    private void completeApplicationTask(RunningGenerationTask task, ApplicationChatResult result) {
        task.reconcileFinalResult(result.answer(), result.tokenUsage());
        completeTask(task, result.modelInstanceId());
    }

    /**
     * 模型流正常结束：持久化完整回答和 Token 用量，并发布完成事件。
     */
    private void completeTask(RunningGenerationTask task, Long modelInstanceId) {
        // beginTerminal 保证完成、失败、取消等并发终态中只有一个能够成功执行。
        if (!task.beginTerminal()) {
            return;
        }
        try {
            conversationService.completeAssistant(
                    task.id(), task.answer(), task.tokenUsage(), modelInstanceId);
            task.publishTerminal(ChatGenerationEventBO.completed());
            // 发布领域事件，触发记忆后处理（摘要、画像、交互记录）
            eventPublisher.publishEvent(new AssistantCompletedEvent(
                    task.userId(), task.turn(), task.applicationVersionId(), task.memoryConfig()));
        } catch (Exception exception) {
            log.error("模型回答完成后持久化失败，assistantMessageId={}", task.id(), exception);
            task.publishTerminal(ChatGenerationEventBO.failed());
        } finally {
            runningTasks.remove(task.id(), task);
        }
    }

    /**
     * 模型流异常结束：保留已生成的部分回答，记录统一错误信息并发布失败事件。
     */
    private void failTask(RunningGenerationTask task, Throwable exception) {
        if (!task.beginTerminal()) {
            return;
        }
        try {
            conversationService.failAssistant(task.id(), task.answer(), MODEL_ERROR_CODE, MODEL_ERROR_MESSAGE, task.tokenUsage());
        } catch (RuntimeException persistenceException) {
            log.error("模型调用失败后更新消息状态失败，assistantMessageId={}", task.id(), persistenceException);
        } finally {
            task.publishTerminal(ChatGenerationEventBO.failed());
            runningTasks.remove(task.id(), task);
            log.error("模型流式对话调用失败，assistantMessageId={}", task.id(), exception);
        }
    }

    /**
     * 用户主动取消：先停止上游模型订阅，再保存部分回答和当前 Token 用量。
     */
    private void cancelTask(RunningGenerationTask task) {
        if (!task.beginTerminal()) {
            return;
        }
        task.disposeModelSubscription();
        try {
            conversationService.cancelAssistant(task.id(), task.answer(), task.tokenUsage());
        } finally {
            task.publishTerminal(ChatGenerationEventBO.cancelled());
            runningTasks.remove(task.id(), task);
        }
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
            task.publishTerminal(ChatGenerationEventBO.failed());
            runningTasks.remove(task.id(), task);
        }
    }

    /**
     * 当进程内任务已不存在时，根据数据库消息恢复一个可直接结束 SSE 的事件流。
     *
     * <p>若数据库保存了部分或完整文本，先发送 SNAPSHOT；随后根据消息状态发送终态事件。
     * 如果数据库仍为 GENERATING，说明原任务所在进程异常退出，将其修正为 INTERRUPTED。</p>
     */
    private Flux<ChatGenerationEventBO> restoreTerminalEvents(ConversationMessageEntity message) {
        Flux<ChatGenerationEventBO> snapshot = Flux.empty();
        if (CharSequenceUtil.isNotEmpty(message.getContent())) {
            snapshot = Flux.just(ChatGenerationEventBO.snapshot(message.getContent()));
        }

        // 生成中但当前进程不存在对应任务 → 修正为 INTERRUPTED
        if (message.getStatus() == MessageStatus.GENERATING) {
            conversationService.interruptAssistant(message.getId(), message.getContent(), TokenUsage.empty());
        }

        ChatGenerationEventBO terminal = statusToTerminalEvent(message.getStatus());
        return snapshot.concatWithValues(terminal);
    }

    /**
     * 将消息终态映射为对应的 SSE 终态事件。
     */
    private ChatGenerationEventBO statusToTerminalEvent(MessageStatus status) {
        return switch (status) {
            case COMPLETED -> ChatGenerationEventBO.completed();
            case CANCELLED -> ChatGenerationEventBO.cancelled();
            case FAILED, INTERRUPTED, GENERATING -> ChatGenerationEventBO.failed();
        };
    }

    /**
     * 查询 Assistant 消息并同时校验消息属于当前用户，防止越权续接或取消他人任务。
     */
    private ConversationMessageEntity requireOwnedAssistantMessage(Long assistantMessageId, Long userId) {
        return messageRepo.findOwnedAssistantMessage(assistantMessageId, userId).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "模型生成任务不存在"));
    }

}
