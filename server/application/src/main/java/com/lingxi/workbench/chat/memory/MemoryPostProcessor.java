package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.message.ConversationCompletedMessage;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import com.lingxi.workbench.observability.common.enums.AiObservationStage;
import com.lingxi.workbench.observability.service.StageObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RocketMQ 会话完成事件的记忆处理入口。
 * <p>
 * 三条管线由不同 Consumer Group 调用，独立重试和进入死信队列；
 * 本组件不再吞掉异常，也不依赖进程内 Spring 事件保证可靠性。
 */
@Component
@RequiredArgsConstructor
public class MemoryPostProcessor {

    private static final int EXTRACTION_MAX_ROUNDS = 50;
    private static final int EXTRACTION_MAX_TOKENS = 16_000;

    private final MemoryEventResolver eventResolver;
    private final ConversationMemoryService conversationMemoryService;
    private final ConversationSummaryMemoryService summaryService;
    private final UserProfileMemoryService profileService;
    private final InteractionRecordMemoryService interactionService;
    private final StageObserver aiStageObserver;

    /**
     * 消费会话摘要任务；重复事件由摘要覆盖水位和唯一约束共同幂等。
     */
    public void processSummary(ConversationCompletedMessage message) {
        aiStageObserver.observe(AiObservationStage.MEMORY_POST, () -> {
            AssistantCompletedEvent event = eventResolver.resolve(message);
            MemoryConfig config = event.memoryConfig();
            if (config.summary().isEnabled()) {
                summaryService.generate(event, config);
            }
        });
    }

    /**
     * 消费用户画像任务；画像来源消息水位阻止重复或旧事件覆盖新画像。
     */
    public void processProfile(ConversationCompletedMessage message) {
        aiStageObserver.observe(AiObservationStage.MEMORY_POST, () -> {
            AssistantCompletedEvent event = eventResolver.resolve(message);
            MemoryConfig config = event.memoryConfig();
            if (!config.profile().isEnabled()) {
                return;
            }
            List<ChatMemoryMessageBO> messages = loadHistory(event);
            if (!messages.isEmpty()) {
                profileService.extract(event, buildHistory(messages), config);
            }
        });
    }

    /**
     * 消费交互记录任务；Assistant 消息唯一键保证记录和向量只落库一次。
     */
    public void processInteraction(ConversationCompletedMessage message) {
        aiStageObserver.observe(AiObservationStage.MEMORY_POST, () -> {
            AssistantCompletedEvent event = eventResolver.resolve(message);
            MemoryConfig config = event.memoryConfig();
            if (!config.interactionRecord().isEnabled()) {
                return;
            }
            List<ChatMemoryMessageBO> messages = loadHistory(event);
            if (!messages.isEmpty()) {
                interactionService.extract(event, buildHistory(messages), config);
            }
        });
    }

    /**
     * 按固定提取上限加载已经完成的会话窗口，失败交给 RocketMQ 有界重试。
     */
    private List<ChatMemoryMessageBO> loadHistory(AssistantCompletedEvent event) {
        return conversationMemoryService.getWindowMemory(
                event.userId(),
                event.turn().conversationId(),
                Integer.MAX_VALUE,
                EXTRACTION_MAX_ROUNDS,
                EXTRACTION_MAX_TOKENS);
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
