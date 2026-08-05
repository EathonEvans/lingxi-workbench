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

/**
 * Supervisor Agent 专属的会话上下文准备策略。
 *
 * <p>与普通 RAG_CHAT Agent 的关键区别：</p>
 * <ul>
 *   <li><b>禁止问题改写</b>：Supervisor 需要理解用户原始意图才能正确委派，
 *       改写会丢失用户的精确表述。配置了 rewritePolicy 会导致启动失败。</li>
 *   <li><b>禁止知识库检索</b>：Supervisor 本身不直接回答业务问题，
 *       而是委派给成员 Agent。绑定了知识库或检索配置会导致启动失败。</li>
 *   <li><b>使用独立处理链</b>：通过 {@code @Qualifier("supervisorChatProcessChain")}
 *       注入 Supervisor 专属的 ChatProcessChain，其中会注入成员 Agent 列表和协作上下文。</li>
 * </ul>
 *
 * <p>策略注册：通过 {@link AgentChatPreparationStrategyRegistry} 按 {@code agentType()}
 * 自动发现并注册。运行时根据 AgentVersion 中的 agentType 字段选择对应策略。</p>
 *
 * <p>上下文构建流程：</p>
 * <ol>
 *   <li>校验：禁止 rewrite 和 knowledge/retrieval 配置</li>
 *   <li>构建基础上下文：加载 System Prompt、User Prompt、记忆策略、运行时变量</li>
 *   <li>禁用问题改写（{@code QueryRewriteConfig.disabled()}）</li>
 *   <li>通过 Supervisor 专属处理链注入：成员 Agent 列表、委派工具、协作限制</li>
 * </ol>
 */
@Component
public class SupervisorPreparationStrategy extends AbstractAgentChatPreparationStrategy {

    public SupervisorPreparationStrategy(
            PromptRuntimeService promptRuntimeService,
            @Qualifier("supervisorChatProcessChain") ChatProcessChain processChain) {
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
            throw new BusinessException(HttpStatusEnum.CONFLICT,
                    "Supervisor Agent不支持问题改写与知识检索");
        }
        if (Objects.nonNull(definition.getKnowledge()) || Objects.nonNull(definition.getRetrievalPolicy())) {
            throw new BusinessException(HttpStatusEnum.CONFLICT,
                    "Supervisor AgentVersion不能包含知识库或检索配置");
        }
        ModelChatContext context = commonContext(definition, execution)
                .queryRewriteConfig(QueryRewriteConfig.disabled())
                .build();
        return process(context);
    }
}