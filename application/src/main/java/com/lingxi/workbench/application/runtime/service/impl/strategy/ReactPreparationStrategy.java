package com.lingxi.workbench.application.runtime.service.impl.strategy;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.AgentChatPreparationStrategy;
import com.lingxi.workbench.application.runtime.service.PromptRuntimeService;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.service.impl.ModelRequestFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.REACT_AGENT_TYPE;

/** ReAct Agent直接消费平台共享上下文，不执行旧问题改写、自动RAG或Prompt节点。 */
@Component
@RequiredArgsConstructor
public class ReactPreparationStrategy implements AgentChatPreparationStrategy {
    private final PromptRuntimeService promptRuntimeService;
    private final ModelRequestFactory modelRequestFactory;

    @Override
    public String agentType() {
        return REACT_AGENT_TYPE;
    }

    @Override
    public ModelChatContext prepare(AgentRuntimeDefinition definition, ChatExecutionContext execution) {
        PromptRuntimeSnapshot userPrompt = Objects.isNull(definition.getUserPromptVersionId())
                ? null
                : promptRuntimeService.resolvePublished(definition.getUserPromptVersionId());
        ModelChatContext context = ModelChatContext.builder()
                .runtimeVariables(execution.getRuntimeVariables())
                .conversationContext(execution.getConversationContext())
                .memoryConfig(execution.getMemoryConfig())
                .systemPrompt(promptRuntimeService.resolvePublished(definition.resolveSystemPromptVersionId()))
                .userPrompt(userPrompt)
                .turn(execution.getTurn())
                .modelUsageRecorder(execution::recordUsage)
                .build();
        context.setPromptPair(modelRequestFactory.createDirectPrompt(context));
        return context;
    }
}
