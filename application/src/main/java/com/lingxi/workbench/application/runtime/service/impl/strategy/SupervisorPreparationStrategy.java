package com.lingxi.workbench.application.runtime.service.impl.strategy;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.service.PromptRuntimeService;
import com.lingxi.workbench.chat.model.bo.QueryRewriteConfig;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;
import com.lingxi.workbench.chat.service.ChatProcessChain;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.SUPERVISOR_AGENT_TYPE;

/** Supervisor 专属的历史加载与直接 Prompt 构建策略。 */
@Component
public class SupervisorPreparationStrategy extends AbstractAgentChatPreparationStrategy {

    public SupervisorPreparationStrategy(PromptRuntimeService promptRuntimeService, @Qualifier("supervisorChatProcessChain") ChatProcessChain processChain) {
        super(promptRuntimeService, processChain);
    }

    @Override
    public String agentType() {
        return SUPERVISOR_AGENT_TYPE;
    }

    @Override
    public ModelChatContext prepare(AgentRuntimeDefinition definition, ChatExecutionContext execution) {
        AgentRuntimeDefinition.RewritePolicy rewrite = definition.getRewritePolicy();
        if (Objects.nonNull(rewrite) && Boolean.TRUE.equals(rewrite.getEnabled())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Supervisor Agent不支持问题改写与知识检索");
        }
        if (Objects.nonNull(definition.getKnowledge()) || Objects.nonNull(definition.getRetrievalPolicy())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "Supervisor AgentVersion不能包含知识库或检索配置");
        }
        ModelChatContext context = commonContext(definition, execution)
                .queryRewriteConfig(QueryRewriteConfig.disabled())
                .build();
        return process(context);
    }
}
