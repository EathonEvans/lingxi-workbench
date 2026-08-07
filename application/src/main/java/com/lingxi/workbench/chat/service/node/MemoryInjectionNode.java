package com.lingxi.workbench.chat.service.node;

import com.lingxi.workbench.chat.common.enums.ChatProcessResult;
import com.lingxi.workbench.chat.memory.MemoryContextService;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.service.AbstractChatProcessNode;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * 旧 AgentVersion 处理链的记忆变量投影节点。
 * 优先复用共享上下文已经准备的摘要和长期记忆，仅在旧调用方没有共享上下文时兼容查询。
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class MemoryInjectionNode extends AbstractChatProcessNode {

    private final MemoryContextService memoryContextService;

    @Override
    protected ChatProcessResult processCurrent(ModelChatContext context) {
        ConversationContext conversationContext = context.getConversationContext();
        if (conversationContext != null) {
            injectPreparedContext(context, conversationContext);
            return ChatProcessResult.CONTINUE;
        }
        MemoryConfig config = context.getMemoryConfig();
        if (config == null || (!config.profile().isEnabled() && !config.interactionRecord().isEnabled())) {
            return ChatProcessResult.CONTINUE;
        }

        Long userId = context.getRuntimeVariables().require(RuntimeVariableAccessors.USER_ID);
        String query = context.getRuntimeVariables().require(RuntimeVariableAccessors.USER_QUERY);
        String memory = memoryContextService.build(userId, query, config);
        if (!memory.isBlank()) {
            context.getRuntimeVariables().put(
                    RuntimeVariableAccessors.LONG_TERM_MEMORY, memory);
        }
        return ChatProcessResult.CONTINUE;
    }

    private static void injectPreparedContext(ModelChatContext context, ConversationContext conversationContext) {
        if (!conversationContext.getSummary().isBlank()) {
            context.getRuntimeVariables().put(RuntimeVariableAccessors.SESSION_SUMMARY, conversationContext.getSummary());
        }
        if (!conversationContext.getLongTermMemory().isBlank()) {
            context.getRuntimeVariables().put(RuntimeVariableAccessors.LONG_TERM_MEMORY, conversationContext.getLongTermMemory());
        }
    }
}
