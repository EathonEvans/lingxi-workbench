package com.lingxi.workbench.application.runtime.service.impl.strategy;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.service.PromptRuntimeService;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.service.impl.ModelRequestFactory;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Supervisor 直接消费共享上下文，成员工具由 AgentChatService 按协作快照装配。 */
@Component
public class SupervisorPreparationStrategy extends AbstractAgentChatPreparationStrategy {

    public SupervisorPreparationStrategy(PromptRuntimeService promptRuntimeService, ModelRequestFactory modelRequestFactory) {
        super(promptRuntimeService, modelRequestFactory);
    }

    @Override
    public AgentType supportedAgentType() {
        return AgentType.SUPERVISOR;
    }

    @Override
    public ModelChatContext prepare(AgentRuntimeDefinition definition, ChatExecutionContext execution) {
        if (Objects.nonNull(definition.getKnowledgeSearch())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Supervisor AgentVersion不能绑定知识搜索");
        }
        return prepareDirect(definition, execution);
    }
}
