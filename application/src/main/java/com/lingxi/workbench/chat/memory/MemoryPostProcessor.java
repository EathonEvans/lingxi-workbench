package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.observability.common.enums.AiObservationStage;
import com.lingxi.workbench.observability.service.AiStageObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME;

/**
 * Assistant 完成后的异步记忆编排，只负责调度三个相互独立的记忆用例。
 * <p>
 * 监听 {@link AssistantCompletedEvent}，在应用线程池中异步执行三条记忆管线：
 * 会话摘要、用户画像、交互记录。各管线按配置开关独立启停，
 * 单条管线失败仅记录告警日志，不阻塞对话主流程，也不影响其余管线。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryPostProcessor {

    private static final int EXTRACTION_MAX_ROUNDS = 50;
    private static final int EXTRACTION_MAX_TOKENS = 16_000;

    private final ConversationMemoryService conversationMemoryService;
    private final ConversationSummaryMemoryService summaryService;
    private final UserProfileMemoryService profileService;
    private final InteractionRecordMemoryService interactionService;
    private final AiStageObserver aiStageObserver;

    /**
     * Assistant 回复完成事件入口，异步触发记忆后处理。
     * <p>
     * 通过 {@code @Async} 将任务提交到应用线程池，避免记忆写入阻塞对话主流程；
     * 实际执行被包装在 {@code MEMORY_POST} 观测阶段内，便于链路追踪与耗时统计。
     *
     * @param event Assistant 完成事件，携带记忆配置与会话上下文
     */
    @Async(APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    @EventListener
    public void onAssistantCompleted(AssistantCompletedEvent event) {
        aiStageObserver.observe(AiObservationStage.MEMORY_POST, () -> process(event));
    }

    /**
     * 三条记忆管线共享同一个MEMORY_POST父span，单条失败仍按既有隔离策略处理。
     */
    private void process(AssistantCompletedEvent event) {
        MemoryConfig config = event.memoryConfig();
        // 会话摘要：按开关独立执行，不依赖后续的消息窗口加载
        if (config.summary().isEnabled()) {
            run("会话摘要", event, () -> summaryService.generate(event, config));
        }
        boolean profileEnabled = config.profile().isEnabled();
        boolean interactionEnabled = config.interactionRecord().isEnabled();
        // 画像与交互记录均未开启时，无需加载会话消息，提前结束
        if (!profileEnabled && !interactionEnabled) {
            return;
        }
        List<ChatMemoryMessageBO> messages;
        // 加载会话窗口消息（按最大轮次与 token 上限截断）；失败仅告警，不让记忆后处理拖垮主流程
        try {
            messages = conversationMemoryService.getWindowMemory(
                    event.userId(),
                    event.turn().conversationId(),
                    Integer.MAX_VALUE,
                    EXTRACTION_MAX_ROUNDS,
                    EXTRACTION_MAX_TOKENS);
        } catch (RuntimeException exception) {
            log.warn("记忆后处理加载会话失败，conversationId={}", event.turn().conversationId(), exception);
            return;
        }
        // 无历史消息可提取时直接返回，避免以空输入调用 LLM 提取
        if (messages.isEmpty()) {
            return;
        }
        // 画像与交互记录共享同一份历史文本，按开关分别触发提取
        String history = buildHistory(messages);
        if (profileEnabled) {
            run("用户画像", event, () -> profileService.extract(event, history, config));
        }
        if (interactionEnabled) {
            run("交互记录", event, () -> interactionService.extract(event, history, config));
        }
    }

    /**
     * 执行单条记忆管线并隔离异常。
     * <p>
     * 统一捕获 {@link RuntimeException} 并输出告警日志，
     * 保证某条管线失败不会中断其余管线，也不会向上抛出到异步线程池。
     *
     * @param operation 管线名称（如"会话摘要"），用于日志定位
     * @param event     当前完成事件，提供 assistantMessageId 等上下文
     * @param action    具体的记忆提取动作
     */
    private void run(String operation, AssistantCompletedEvent event, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.warn("记忆后处理失败，operation={}，assistantMessageId={}", operation, event.turn().assistantMessageId(), exception);
        }
    }

    /**
     * 将会话窗口消息拼装为统一的记忆提取文本。
     * <p>
     * 每条消息按 "角色: 内容" 格式逐行拼接，供用户画像与交互记录
     * 两条管线复用；保持消息原始顺序，减少 LLM 上下文理解偏差。
     *
     * @param messages 会话窗口消息列表
     * @return 拼接后的历史文本
     */
    private static String buildHistory(List<ChatMemoryMessageBO> messages) {
        StringBuilder history = new StringBuilder();
        // 按 "角色: 内容" 逐行拼接，保留会话原始顺序
        for (ChatMemoryMessageBO message : messages) {
            history.append(message.role().getCode())
                    .append(": ")
                    .append(message.content())
                    .append("\n");
        }
        return history.toString();
    }
}
