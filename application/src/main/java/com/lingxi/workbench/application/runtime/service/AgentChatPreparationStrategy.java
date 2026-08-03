package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.chat.model.ccontext.ModelChatContext;

/** 按 Agent 类型准备模型上下文并执行对应的前置处理链。 */
public interface AgentChatPreparationStrategy {

    /** 返回本策略支持的稳定 Agent 类型编码。 */
    String agentType();

    /** 校验类型专属配置、构建上下文并执行该类型的有序处理链。 */
    ModelChatContext prepare(AgentRuntimeDefinition definition, ChatExecutionContext execution);
}
