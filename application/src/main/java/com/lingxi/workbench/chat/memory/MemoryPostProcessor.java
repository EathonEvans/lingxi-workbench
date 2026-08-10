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

/** Assistant 完成后的异步记忆编排，只负责调度三个相互独立的记忆用例。 */
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

    @Async
    @EventListener
    public void onAssistantCompleted(AssistantCompletedEvent event) {
        aiStageObserver.observe(AiObservationStage.MEMORY_POST, () -> process(event));
    }

    /** 三条记忆管线共享同一个MEMORY_POST父span，单条失败仍按既有隔离策略处理。 */
    private void process(AssistantCompletedEvent event) {
        MemoryConfig config = event.memoryConfig();
        if (config.summary().isEnabled()) {
            run("会话摘要", event, () -> summaryService.generate(event, config.summary()));
        }
        boolean profileEnabled = config.profile().isEnabled();
        boolean interactionEnabled = config.interactionRecord().isEnabled();
        if (!profileEnabled && !interactionEnabled) {
            return;
        }
        List<ChatMemoryMessageBO> messages;
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
        if (messages.isEmpty()) {
            return;
        }
        String history = buildHistory(messages);
        if (profileEnabled) {
            run("用户画像", event, () -> profileService.extract(event, history, config.profile()));
        }
        if (interactionEnabled) {
            run("交互记录", event, () -> interactionService.extract(event, history, config.interactionRecord()));
        }
    }

    private void run(String operation, AssistantCompletedEvent event, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            log.warn("记忆后处理失败，operation={}，assistantMessageId={}", operation, event.turn().assistantMessageId(), exception);
        }
    }

    private static String buildHistory(List<ChatMemoryMessageBO> messages) {
        StringBuilder history = new StringBuilder();
        for (ChatMemoryMessageBO message : messages) {
            history.append(message.role().getCode())
                    .append(": ")
                    .append(message.content())
                    .append("\n");
        }
        return history.toString();
    }
}
