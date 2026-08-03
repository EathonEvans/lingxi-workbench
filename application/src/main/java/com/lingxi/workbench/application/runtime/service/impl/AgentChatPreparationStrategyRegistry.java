package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.service.AgentChatPreparationStrategy;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Agent 类型到前置处理策略的只读注册表。 */
@Component
public class AgentChatPreparationStrategyRegistry {

    private final Map<String, AgentChatPreparationStrategy> strategies;

    public AgentChatPreparationStrategyRegistry(List<AgentChatPreparationStrategy> strategies) {
        this.strategies = strategies.stream().collect(Collectors.toUnmodifiableMap(AgentChatPreparationStrategy::agentType, Function.identity()));
    }

    /** 查找 Agent 类型对应策略；未知类型明确拒绝运行。 */
    public AgentChatPreparationStrategy require(String agentType) {
        AgentChatPreparationStrategy strategy = strategies.get(agentType);
        if (Objects.isNull(strategy)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前运行时不支持Agent类型：" + agentType);
        }
        return strategy;
    }
}
