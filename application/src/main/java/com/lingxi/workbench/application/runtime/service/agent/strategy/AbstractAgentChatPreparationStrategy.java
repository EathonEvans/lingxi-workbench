package com.lingxi.workbench.application.runtime.service.agent.strategy;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.agent.AgentChatPreparationStrategy;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.service.impl.ModelRequestFactory;

import java.util.Objects;

/**
 * ReAct 与 Supervisor 直接消费共享上下文时复用的模型请求准备逻辑。
 */
public abstract class AbstractAgentChatPreparationStrategy implements AgentChatPreparationStrategy {

    private final PromptRuntimeService promptRuntimeService;
    private final ModelRequestFactory modelRequestFactory;

    protected AbstractAgentChatPreparationStrategy(PromptRuntimeService promptRuntimeService, ModelRequestFactory modelRequestFactory) {
        this.promptRuntimeService = promptRuntimeService;
        this.modelRequestFactory = modelRequestFactory;
    }

    /**
     * 构建可以直接交给模型网关的上下文。
     */
    protected ModelChatContext prepareDirect(AgentRuntimeDefinition definition, ChatExecutionContext execution) {
        PromptRuntimeSnapshot userPrompt = Objects.isNull(definition.getUserPromptVersionId())
                ? null
                : promptRuntimeService.resolvePublished(definition.getUserPromptVersionId());
        ModelChatContext context = ModelChatContext.builder()
                .runtimeVariables(execution.getRuntimeVariables())
                .systemPrompt(promptRuntimeService.resolvePublished(definition.getSystemPromptVersionId()))
                .userPrompt(userPrompt)
                .build();
        context.setPromptPair(modelRequestFactory.createDirectPrompt(context));
        return context;
    }
}
