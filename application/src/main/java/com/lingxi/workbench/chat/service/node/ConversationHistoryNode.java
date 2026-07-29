package com.lingxi.workbench.chat.service.node;

import com.lingxi.workbench.chat.common.enums.MessageRole;
import com.lingxi.workbench.chat.common.enums.ChatProcessResult;
import com.lingxi.workbench.chat.model.bo.ChatMemoryMessageBO;
import com.lingxi.workbench.chat.model.bo.QueryRewriteConfig;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.service.AbstractChatProcessNode;
import com.lingxi.workbench.chat.service.ConversationMemoryService;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 加载当前用户消息之前的窗口历史。
 */
@Component
@RequiredArgsConstructor
public class ConversationHistoryNode extends AbstractChatProcessNode {

    private final ConversationMemoryService conversationMemoryService;

    /**
     * 加载当前用户消息之前的窗口记忆，根据记忆策略选择配置项或默认限制，
     * 将业务消息角色映射为模型上下文所需的标准角色。
     */
    @Override
    protected ChatProcessResult processCurrent(ModelChatContext context) {
        List<ChatHistoryMessage> answerHistory = toHistoryMessages(
                loadAnswerMemory(context));
        context.getRuntimeVariables().put(
                RuntimeVariableAccessors.SESSION_HISTORY,
                answerHistory);

        /*
         * 回答记忆与问题改写历史是两个独立窗口。即使回答阶段关闭会话记忆，
         * 改写仍可读取少量最近对话来消解“它、这个、那价格呢”等指代。
         */
        List<ChatHistoryMessage> rewriteHistory = toHistoryMessages(
                loadRewriteMemory(context));
        context.getRuntimeVariables().put(
                RuntimeVariableAccessors.QUERY_REWRITE_HISTORY,
                rewriteHistory);
        return ChatProcessResult.CONTINUE;
    }

    /**
     * Agent 没有覆盖记忆策略时使用平台默认窗口；显式关闭记忆时不访问消息表。
     *
     * <p>这里区分“没有配置”和“配置为 0”：前者使用平台默认值，后者由
     * {@link ConversationMemoryService} 按关闭窗口处理。</p>
     */
    private List<ChatMemoryMessageBO> loadAnswerMemory(ModelChatContext context) {
        if (Boolean.FALSE.equals(context.getMemoryEnabled())) {
            return List.of();
        }
        Long userId = context.getRuntimeVariables().require(RuntimeVariableAccessors.USER_ID);
        Long sessionId = context.getRuntimeVariables().require(RuntimeVariableAccessors.SESSION_ID);
        if (context.getMemoryMaxRounds() != null && context.getMemoryMaxTokens() != null) {
            return conversationMemoryService.getWindowMemory(userId, sessionId, context.getTurn().userSequenceNo(), context.getMemoryMaxRounds(), context.getMemoryMaxTokens());
        }
        return conversationMemoryService.getWindowMemory(userId, sessionId, context.getTurn().userSequenceNo());
    }

    private List<ChatMemoryMessageBO> loadRewriteMemory(ModelChatContext context) {
        QueryRewriteConfig config = context.getQueryRewriteConfig();
        if (config == null || !config.isEnabled()) {
            return List.of();
        }
        Long userId = context.getRuntimeVariables().require(
                RuntimeVariableAccessors.USER_ID);
        Long sessionId = context.getRuntimeVariables().require(
                RuntimeVariableAccessors.SESSION_ID);
        return conversationMemoryService.getWindowMemory(
                userId,
                sessionId,
                context.getTurn().userSequenceNo(),
                config.getMaxHistoryRounds(),
                config.getMaxHistoryTokens());
    }

    private static List<ChatHistoryMessage> toHistoryMessages(
            List<ChatMemoryMessageBO> messages) {
        return messages.stream()
                .map(message -> new ChatHistoryMessage(
                        toHistoryRole(message.role()),
                        message.content()))
                .toList();
    }

    private static ChatHistoryMessage.Role toHistoryRole(MessageRole role) {
        return switch (role) {
            case USER -> ChatHistoryMessage.Role.USER;
            case ASSISTANT -> ChatHistoryMessage.Role.ASSISTANT;
        };
    }
}
