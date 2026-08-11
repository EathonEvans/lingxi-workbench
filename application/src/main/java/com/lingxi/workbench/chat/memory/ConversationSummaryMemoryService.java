package com.lingxi.workbench.chat.memory;

import com.lingxi.workbench.chat.memory.event.AssistantCompletedEvent;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.ConversationSummarySource;
import com.lingxi.workbench.chat.model.bo.ConversationSummaryTrigger;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.workflow.model.ConversationSummaryMemoryConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/** 按新增完整轮次生成滚动会话摘要。 */
@Service
@RequiredArgsConstructor
public class ConversationSummaryMemoryService {

    private static final int SUMMARY_INPUT_TOKEN_BUDGET = 16_000;

    private final ConversationMemoryService conversationMemoryService;
    private final MemoryCompletionService completionService;
    private final ConversationSummaryPersistenceService persistenceService;

    /**
     * 在上一份摘要基础上生成滚动摘要。
     * 若本轮新增轮次不满足触发阈值则跳过，否则通过 MemoryCompletionService 生成摘要并持久化。
     */
    public void generate(AssistantCompletedEvent event, ConversationSummaryMemoryConfig config) {
        ConversationSummaryTrigger trigger = new ConversationSummaryTrigger(config.effectiveMinRounds(), config.effectiveMinTokens(), SUMMARY_INPUT_TOKEN_BUDGET);
        Optional<ConversationSummarySource> optionalSource = conversationMemoryService.loadSummarySource(event.userId(), event.turn(), trigger);
        if (optionalSource.isEmpty()) {
            return;
        }
        ConversationSummarySource source = optionalSource.get();
        String summary = completionService.complete(
                config.getPromptVersionId(),
                config.getChatModelInstanceId(),
                Map.of("conversation_history", buildRollingHistory(source)));
        persistenceService.saveIfCurrent(event, source, summary, config);
    }

    private static String buildRollingHistory(ConversationSummarySource source) {
        StringBuilder history = new StringBuilder();
        if (!source.previousSummary().isBlank()) {
            history.append("已有会话摘要：\n")
                    .append(source.previousSummary())
                    .append("\n\n新增完整对话：\n");
        }
        for (ChatMemoryMessageBO message : source.messages()) {
            history.append(message.role().getCode())
                    .append(": ")
                    .append(message.content())
                    .append('\n');
        }
        return history.toString();
    }
}
