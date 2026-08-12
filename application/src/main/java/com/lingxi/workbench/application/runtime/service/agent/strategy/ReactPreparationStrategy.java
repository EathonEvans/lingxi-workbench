package com.lingxi.workbench.application.runtime.service.agent.strategy;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.service.prompt.PromptRuntimeService;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.service.impl.ModelRequestFactory;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import org.springframework.stereotype.Component;

/**
 * ReAct Agent直接消费平台共享上下文，并按发布快照装配可调用工具。
 */
@Component
public class ReactPreparationStrategy extends AbstractAgentChatPreparationStrategy {

    public ReactPreparationStrategy(PromptRuntimeService promptRuntimeService, ModelRequestFactory modelRequestFactory) {
        super(promptRuntimeService, modelRequestFactory);
    }

    @Override
    public AgentType supportedAgentType() {
        return AgentType.REACT;
    }

    @Override
    public ModelChatContext prepare(AgentRuntimeDefinition definition, ChatExecutionContext execution) {
        return prepareDirect(definition, execution);
    }
}
