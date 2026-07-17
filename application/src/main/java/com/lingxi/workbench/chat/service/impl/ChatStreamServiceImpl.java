package com.lingxi.workbench.chat.service.impl;

import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatStreamResult;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.MessageTokenUsageBO;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.model.request.StreamChatRequest;
import com.lingxi.workbench.chat.model.vo.ChatStartVO;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.service.ChatStreamService;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.StreamingChatGateway;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流式对话服务实现。
 *
 * <p>模型生成任务由服务端主动订阅并独立运行，SSE 连接只是任务观察者。
 * 浏览器断开连接不会取消模型调用，只有显式取消请求才会终止任务。</p>
 */
@Service
@RequiredArgsConstructor
public class ChatStreamServiceImpl implements ChatStreamService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatStreamServiceImpl.class);
    private static final String MODEL_ERROR_CODE = "AI_MODEL_ERROR";
    private static final String MODEL_ERROR_MESSAGE = "模型服务调用失败";
    private static final String TASK_START_ERROR_CODE = "GENERATION_START_ERROR";
    private static final String TASK_START_ERROR_MESSAGE = "模型生成任务启动失败";

    private final StreamingChatGateway streamingChatGateway;
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    private final ConversationService conversationService;
    private final ConversationMemoryService conversationMemoryService;
    private final ConversationMessageRepo messageRepo;

    /**
     * 当前进程正在执行的模型生成任务，任务 ID 为 Assistant 消息 ID。
     */
    private final Map<Long, RunningGenerationTask> runningTasks = new ConcurrentHashMap<>();

    @Override
    public ChatStreamResult stream(StreamChatRequest request, Long userId) {
        String prompt = request.prompt().trim();
        ChatModelRuntimeConfig modelConfig = modelRuntimeConfigService.getDefaultChatModel();
        ChatTurnBO turn = conversationService.startTurn(userId, Long.valueOf(request.conversationId()), request.clientMessageId(), prompt, modelConfig.instanceId());
        RunningGenerationTask task = new RunningGenerationTask(turn.assistantMessageId());
        try {
            List<ChatHistoryMessage> history = conversationMemoryService.getWindowMemory(userId, turn.conversationId(), turn.userSequenceNo()).stream().map(message -> new ChatHistoryMessage(ChatHistoryMessage.Role.valueOf(message.role().name()), message.content())).toList();
            runningTasks.put(turn.assistantMessageId(), task);
            startGeneration(task, new ChatStreamCommand(prompt, history, modelConfig));
        } catch (RuntimeException exception) {
            runningTasks.remove(turn.assistantMessageId());
            try {
                conversationService.failAssistant(
                        turn.assistantMessageId(),
                        "",
                        TASK_START_ERROR_CODE,
                        TASK_START_ERROR_MESSAGE,
                        MessageTokenUsageBO.empty());
            } catch (RuntimeException persistenceException) {
                exception.addSuppressed(persistenceException);
                LOGGER.error("模型生成任务启动失败后更新消息状态失败，assistantMessageId={}", turn.assistantMessageId(), persistenceException);
            }
            throw exception;
        }

        ChatStartVO start = new ChatStartVO(String.valueOf(turn.conversationId()), String.valueOf(turn.userMessageId()), String.valueOf(turn.assistantMessageId()));
        return new ChatStreamResult(start, task.eventsAfter(0));
    }

    @Override
    public Flux<ChatGenerationEventBO> resume(Long assistantMessageId, long afterSequence, Long userId) {
        requireOwnedAssistantMessage(assistantMessageId, userId);
        RunningGenerationTask task = runningTasks.get(assistantMessageId);
        if (task != null) {
            return task.eventsAfter(afterSequence);
        }
        // 查询与任务结束可能并发发生，任务已移除时重新读取一次数据库终态。
        return restoreTerminalEvents(requireOwnedAssistantMessage(assistantMessageId, userId));
    }

    @Override
    public void cancel(Long assistantMessageId, Long userId) {
        ConversationMessageEntity message = requireOwnedAssistantMessage(assistantMessageId, userId);
        RunningGenerationTask task = runningTasks.get(assistantMessageId);
        if (task != null) {
            cancelTask(task);
            return;
        }
        if (message.getStatus() == MessageStatus.GENERATING) {
            conversationService.cancelAssistant(
                    assistantMessageId, message.getContent(), MessageTokenUsageBO.empty());
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
                LOGGER.error("应用关闭时更新模型任务状态失败，assistantMessageId={}", task.id(), exception);
            }
        }
    }

    private void startGeneration(RunningGenerationTask task, ChatStreamCommand command) {
        Disposable subscription = Flux.defer(() -> streamingChatGateway.stream(command))
                .subscribe(task::acceptChunk, exception -> failTask(task, exception),
                        () -> completeTask(task));
        task.setSubscription(subscription);
    }

    private void completeTask(RunningGenerationTask task) {
        if (!task.beginTerminal()) {
            return;
        }
        try {
            conversationService.completeAssistant(
                    task.id(), task.answer(), task.tokenUsage());
            task.publishTerminal(ChatGenerationEventBO.completed());
        } catch (Exception exception) {
            LOGGER.error("模型回答完成后持久化失败，assistantMessageId={}", task.id(), exception);
            task.publishTerminal(ChatGenerationEventBO.failed());
        } finally {
            runningTasks.remove(task.id(), task);
        }
    }

    private void failTask(RunningGenerationTask task, Throwable exception) {
        if (!task.beginTerminal()) {
            return;
        }
        try {
            conversationService.failAssistant(
                    task.id(),
                    task.answer(),
                    MODEL_ERROR_CODE,
                    MODEL_ERROR_MESSAGE,
                    task.tokenUsage());
        } catch (RuntimeException persistenceException) {
            LOGGER.error("模型调用失败后更新消息状态失败，assistantMessageId={}", task.id(), persistenceException);
        } finally {
            task.publishTerminal(ChatGenerationEventBO.failed());
            runningTasks.remove(task.id(), task);
            LOGGER.error("模型流式对话调用失败，assistantMessageId={}", task.id(), exception);
        }
    }

    private void cancelTask(RunningGenerationTask task) {
        if (!task.beginTerminal()) {
            return;
        }
        task.disposeModelSubscription();
        try {
            conversationService.cancelAssistant(
                    task.id(), task.answer(), task.tokenUsage());
        } finally {
            task.publishTerminal(ChatGenerationEventBO.cancelled());
            runningTasks.remove(task.id(), task);
        }
    }

    private void interruptTask(RunningGenerationTask task) {
        if (!task.beginTerminal()) {
            return;
        }
        task.disposeModelSubscription();
        try {
            conversationService.interruptAssistant(
                    task.id(), task.answer(), task.tokenUsage());
        } finally {
            task.publishTerminal(ChatGenerationEventBO.failed());
            runningTasks.remove(task.id(), task);
        }
    }

    private Flux<ChatGenerationEventBO> restoreTerminalEvents(ConversationMessageEntity message) {
        Flux<ChatGenerationEventBO> snapshot = hasText(message.getContent()) ? Flux.just(ChatGenerationEventBO.snapshot(message.getContent())) : Flux.empty();
        ChatGenerationEventBO terminal = switch (message.getStatus()) {
            case COMPLETED -> ChatGenerationEventBO.completed();
            case CANCELLED -> ChatGenerationEventBO.cancelled();
            case FAILED, INTERRUPTED -> ChatGenerationEventBO.failed();
            case GENERATING -> {
                // 生成中但当前进程不存在对应任务，说明服务曾异常退出。
                conversationService.interruptAssistant(
                        message.getId(), message.getContent(), MessageTokenUsageBO.empty());
                yield ChatGenerationEventBO.failed();
            }
        };
        return snapshot.concatWithValues(terminal);
    }

    private ConversationMessageEntity requireOwnedAssistantMessage(Long assistantMessageId, Long userId) {
        return messageRepo.findOwnedAssistantMessage(assistantMessageId, userId).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "模型生成任务不存在"));
    }

    private static boolean hasText(String content) {
        return content != null && !content.isEmpty();
    }

    /**
     * 单个模型生成任务及其进程内可重放事件。
     */
    private static final class RunningGenerationTask {

        private final Long id;
        private final StringBuilder answer = new StringBuilder();
        private final Sinks.Many<ChatGenerationEventBO> eventSink = Sinks.many().replay().all();

        private long sequence;
        private boolean terminal;
        private Disposable modelSubscription;
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer totalTokens;

        private RunningGenerationTask(Long id) {
            this.id = id;
        }

        private Long id() {
            return id;
        }

        private synchronized void acceptChunk(ChatStreamChunk chunk) {
            if (terminal) {
                return;
            }
            if (chunk.hasUsage()) {
                inputTokens = chunk.inputTokens();
                outputTokens = chunk.outputTokens();
                totalTokens = chunk.totalTokens();
            }
            if (chunk.content() == null || chunk.content().isEmpty()) {
                return;
            }
            answer.append(chunk.content());
            eventSink.emitNext(
                    ChatGenerationEventBO.chunk(++sequence, chunk.content()),
                    Sinks.EmitFailureHandler.FAIL_FAST);
        }

        private Flux<ChatGenerationEventBO> eventsAfter(long afterSequence) {
            return eventSink.asFlux().filter(event -> event.type() != ChatGenerationEventType.CHUNK || event.sequence() > afterSequence);
        }

        private synchronized boolean beginTerminal() {
            if (terminal) {
                return false;
            }
            terminal = true;
            return true;
        }

        private synchronized String answer() {
            return answer.toString();
        }

        private synchronized MessageTokenUsageBO tokenUsage() {
            return new MessageTokenUsageBO(inputTokens, outputTokens, totalTokens);
        }

        private synchronized void setSubscription(Disposable subscription) {
            modelSubscription = subscription;
            if (terminal) {
                subscription.dispose();
            }
        }

        private synchronized void disposeModelSubscription() {
            if (modelSubscription != null) {
                modelSubscription.dispose();
            }
        }

        private void publishTerminal(ChatGenerationEventBO event) {
            eventSink.emitNext(event, Sinks.EmitFailureHandler.FAIL_FAST);
            eventSink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
        }
    }
}
