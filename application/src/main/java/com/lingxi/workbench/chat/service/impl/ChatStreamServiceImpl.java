package com.lingxi.workbench.chat.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.MessageStatus;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.MessageTokenUsageBO;
import com.lingxi.workbench.chat.model.entity.ConversationMessageEntity;
import com.lingxi.workbench.chat.model.request.StartChatRequest;
import com.lingxi.workbench.chat.model.vo.ChatStartVO;
import com.lingxi.workbench.chat.repo.ConversationMessageRepo;
import com.lingxi.workbench.chat.service.ChatStreamService;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.chat.service.ConversationService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatStreamCommand;
import com.lingxi.workbench.infra.ai.chat.StreamingChatGateway;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

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
 * <p>一次完整调用按照“配置加载 → 消息占位落库 → 记忆构建 → 后台模型调用 →
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

    /**
     * 屏蔽 Spring AI 和具体模型供应商差异的流式调用入口。
     */
    private final StreamingChatGateway streamingChatGateway;
    /**
     * 负责查询、校验并解密当前默认对话模型配置。
     */
    private final ModelRuntimeConfigService modelRuntimeConfigService;
    /**
     * 负责会话、用户消息和 Assistant 占位消息的事务性持久化。
     */
    private final ConversationService conversationService;
    /**
     * 负责从历史消息中裁剪本轮可使用的上下文窗口。
     */
    private final ConversationMemoryService conversationMemoryService;
    /**
     * 用于校验生成任务归属并在恢复订阅时读取消息终态。
     */
    private final ConversationMessageRepo messageRepo;

    /**
     * 当前进程正在执行的模型生成任务，任务 ID 为 Assistant 消息 ID。
     */
    private final Map<Long, RunningGenerationTask> runningTasks = new ConcurrentHashMap<>();

    /**
     * 创建并启动一次流式模型生成任务。
     *
     * <p>本方法返回时，用户消息和 Assistant 占位消息已经落库，模型生成任务也已由服务端主动订阅。
     * 返回的 {@link Flux} 仅用于观察生成事件，客户端取消订阅不会反向取消模型任务。</p>
     *
     * @param request 用户输入、会话标识和客户端幂等消息标识
     * @param userId  当前登录用户主键，用于校验会话归属
     * @return 已落库的会话、用户消息和 Assistant 消息主键
     */
    @Override
    public ChatStartVO start(StartChatRequest request, Long userId) {
        // Controller 已完成非空校验，此处统一去除用户输入首尾空白，后续落库和模型调用共用同一份文本。
        String prompt = request.prompt().trim();

        // 查询启用中的默认 CHAT 模型，同时校验供应商和密钥状态，并在内存中解密本次调用所需的 API Key。
        ChatModelRuntimeConfig modelConfig = modelRuntimeConfigService.getDefaultChatModel();

        // 在单个数据库事务中锁定会话，写入已完成的用户消息和 GENERATING 状态的 Assistant 占位消息。
        // 必须先完成落库再调用模型，保证即使模型启动失败，本轮消息仍有可追溯的持久化记录。
        ChatTurnBO turn = conversationService.startTurn(
                userId,
                Long.valueOf(request.conversationId()),
                request.clientMessageId(),
                prompt,
                modelConfig.getInstanceId());

        // Assistant 消息主键同时作为生成任务主键，便于 SSE 续接、取消和数据库终态恢复使用同一标识。
        RunningGenerationTask task = new RunningGenerationTask(turn.assistantMessageId());
        try {
            // 只读取当前用户消息之前已经完成的问答轮次，并根据轮数和 Token 上限裁剪上下文。
            // userSequenceNo 是当前用户消息序号，传入它可以避免把本轮刚落库的消息重复加入历史记忆。
            List<ChatHistoryMessage> history = conversationMemoryService
                    .getWindowMemory(userId, turn.conversationId(), turn.userSequenceNo())
                    .stream()
                    .map(message -> new ChatHistoryMessage(toHistoryRole(message.role()), message.content()))
                    .toList();

            // 先注册任务再启动模型，确保模型快速返回增量时，续接和取消接口已经能够定位到任务。
            runningTasks.put(turn.assistantMessageId(), task);

            // 服务端在此主动订阅模型 Flux。此后模型生成生命周期独立于下面返回给 Controller 的 SSE Flux。
            startGeneration(task, new ChatStreamCommand(prompt, history, modelConfig));
        } catch (RuntimeException exception) {
            // 这里只处理“模型任务成功启动之前”的异常，例如记忆查询、命令构建或订阅创建失败。
            runningTasks.remove(turn.assistantMessageId());
            try {
                // Assistant 占位消息已经落库，启动失败时必须将其从 GENERATING 推进到 FAILED，避免形成僵尸任务。
                conversationService.failAssistant(
                        turn.assistantMessageId(),
                        "",
                        TASK_START_ERROR_CODE,
                        TASK_START_ERROR_MESSAGE,
                        MessageTokenUsageBO.empty());
            } catch (RuntimeException persistenceException) {
                exception.addSuppressed(persistenceException);
                log.error("模型生成任务启动失败后更新消息状态失败，assistantMessageId={}", turn.assistantMessageId(), persistenceException);
            }
            throw exception;
        }

        // Long 主键转为字符串返回前端，避免 JavaScript Number 对大整数产生精度损失。
        return new ChatStartVO(
                String.valueOf(turn.conversationId()),
                String.valueOf(turn.userMessageId()),
                String.valueOf(turn.assistantMessageId()));
    }

    /**
     * 将 application 数据库消息角色转换为 infra-ai 模型消息角色。
     * 两个枚举属于不同模块，显式映射可以避免依赖枚举名称偶然一致。
     */
    private static ChatHistoryMessage.Role toHistoryRole(MessageRole role) {
        return switch (role) {
            case USER -> ChatHistoryMessage.Role.USER;
            case ASSISTANT -> ChatHistoryMessage.Role.ASSISTANT;
        };
    }

    @Override
    public Flux<ChatGenerationEventBO> resume(Long assistantMessageId, long afterSequence, Long userId) {
        requireOwnedAssistantMessage(assistantMessageId, userId);
        RunningGenerationTask task = runningTasks.get(assistantMessageId);
        if (Objects.nonNull(task)) {
            return task.eventsAfter(afterSequence);
        }
        // 查询与任务结束可能并发发生，任务已移除时重新读取一次数据库终态。
        return restoreTerminalEvents(requireOwnedAssistantMessage(assistantMessageId, userId));
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
                log.error("应用关闭时更新模型任务状态失败，assistantMessageId={}", task.id(), exception);
            }
        }
    }

    /**
     * 主动订阅模型流并把 Reactor 的三种信号映射到任务生命周期。
     *
     * <p>{@code next} 处理文本和 Token 用量，{@code error} 将任务落为失败，
     * {@code complete} 将完整回答落库。{@link Flux#defer} 使网关调用发生在订阅阶段，
     * 同步抛出的构建异常也能进入统一的错误处理链路。</p>
     */
    private void startGeneration(RunningGenerationTask task, ChatStreamCommand command) {
        Disposable subscription = Flux.defer(() -> streamingChatGateway.stream(command))
                .subscribe(task::acceptChunk, exception -> failTask(task, exception),
                        () -> completeTask(task));
        task.setSubscription(subscription);
    }

    /**
     * 模型流正常结束：持久化完整回答和 Token 用量，并发布完成事件。
     */
    private void completeTask(RunningGenerationTask task) {
        // beginTerminal 保证完成、失败、取消等并发终态中只有一个能够成功执行。
        if (!task.beginTerminal()) {
            return;
        }
        try {
            conversationService.completeAssistant(
                    task.id(), task.answer(), task.tokenUsage());
            task.publishTerminal(ChatGenerationEventBO.completed());
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
            conversationService.failAssistant(
                    task.id(),
                    task.answer(),
                    MODEL_ERROR_CODE,
                    MODEL_ERROR_MESSAGE,
                    task.tokenUsage());
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
            conversationService.cancelAssistant(
                    task.id(), task.answer(), task.tokenUsage());
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
            conversationService.interruptAssistant(
                    task.id(), task.answer(), task.tokenUsage());
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
        Flux<ChatGenerationEventBO> snapshot = CharSequenceUtil.isNotEmpty(message.getContent()) ? Flux.just(ChatGenerationEventBO.snapshot(message.getContent())) : Flux.empty();
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

    /**
     * 查询 Assistant 消息并同时校验消息属于当前用户，防止越权续接或取消他人任务。
     */
    private ConversationMessageEntity requireOwnedAssistantMessage(Long assistantMessageId, Long userId) {
        return messageRepo.findOwnedAssistantMessage(assistantMessageId, userId).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "模型生成任务不存在"));
    }

    /**
     * 单个模型生成任务及其进程内可重放事件。
     *
     * <p>该对象只存在于当前服务进程，不承担最终持久化职责。所有可能由不同线程调用的状态方法均进行同步，
     * 防止模型回调、用户取消和应用关闭同时修改回答内容或重复进入终态。</p>
     */
    private static final class RunningGenerationTask {

        /**
         * 对应数据库中的 Assistant 消息主键。
         */
        private final Long id;
        /**
         * 按到达顺序累积模型文本增量，终态时一次性保存完整或部分回答。
         */
        private final StringBuilder answer = new StringBuilder();
        /**
         * 进程内事件总线。replay().all() 允许较晚建立的 SSE 订阅者重放本进程已经产生的事件。
         */
        private final Sinks.Many<ChatGenerationEventBO> eventSink = Sinks.many().replay().all();

        /**
         * CHUNK 事件的单调递增序号，用于客户端断线后按序号续传。
         */
        private long sequence;
        /**
         * 是否已经进入完成、失败、取消或中断中的任一终态。
         */
        private boolean terminal;
        /**
         * 服务端持有的模型 Flux 订阅，用户取消或应用关闭时通过它停止上游调用。
         */
        private Disposable modelSubscription;
        /**
         * 最近一个携带真实 Usage 的模型分片返回的 Token 用量。
         */
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer totalTokens;

        private RunningGenerationTask(Long id) {
            this.id = id;
        }

        private Long id() {
            return id;
        }

        /**
         * 接收模型增量，累计文本和 Token 用量，并向所有 SSE 观察者发布 CHUNK 事件。
         */
        private synchronized void acceptChunk(ChatStreamChunk chunk) {
            if (terminal) {
                // 终态之后到达的迟到分片必须丢弃，避免覆盖已经持久化的最终内容。
                return;
            }
            if (chunk.hasUsage()) {
                // 部分供应商只在最后一个无文本分片中返回 Usage，因此用量处理不能依赖 content 非空。
                inputTokens = chunk.inputTokens();
                outputTokens = chunk.outputTokens();
                totalTokens = chunk.totalTokens();
            }
            if (CharSequenceUtil.isEmpty(chunk.content())) {
                // 仅携带 Usage 的空文本分片已经完成用量更新，无需推送无意义的文本事件。
                return;
            }
            answer.append(chunk.content());
            eventSink.emitNext(
                    ChatGenerationEventBO.chunk(++sequence, chunk.content()),
                    Sinks.EmitFailureHandler.FAIL_FAST);
        }

        /**
         * 返回指定 CHUNK 序号之后的事件流。
         * 终态和 SNAPSHOT 事件不使用增量序号，因此始终保留。
         */
        private Flux<ChatGenerationEventBO> eventsAfter(long afterSequence) {
            return eventSink.asFlux().filter(event -> event.type() != ChatGenerationEventType.CHUNK || event.sequence() > afterSequence);
        }

        /**
         * 原子地抢占任务终态处理权，返回 false 表示其他线程已经先一步结束任务。
         */
        private synchronized boolean beginTerminal() {
            if (terminal) {
                return false;
            }
            terminal = true;
            return true;
        }

        /**
         * 获取当前已经累计的完整或部分回答快照。
         */
        private synchronized String answer() {
            return answer.toString();
        }

        /**
         * 获取当前已知的 Token 用量快照；供应商未返回时字段允许为空。
         */
        private synchronized MessageTokenUsageBO tokenUsage() {
            return new MessageTokenUsageBO(inputTokens, outputTokens, totalTokens);
        }

        /**
         * 保存模型订阅。若任务在订阅对象返回前已经被取消，则立即释放该迟到订阅。
         */
        private synchronized void setSubscription(Disposable subscription) {
            modelSubscription = subscription;
            if (terminal) {
                subscription.dispose();
            }
        }

        /**
         * 主动停止上游模型生成；未建立订阅时无需处理。
         */
        private synchronized void disposeModelSubscription() {
            if (Objects.nonNull(modelSubscription)) {
                modelSubscription.dispose();
            }
        }

        /**
         * 发布唯一终态事件并关闭事件流，使 SSE 连接能够正常结束。
         */
        private void publishTerminal(ChatGenerationEventBO event) {
            eventSink.emitNext(event, Sinks.EmitFailureHandler.FAIL_FAST);
            eventSink.emitComplete(Sinks.EmitFailureHandler.FAIL_FAST);
        }
    }
}
