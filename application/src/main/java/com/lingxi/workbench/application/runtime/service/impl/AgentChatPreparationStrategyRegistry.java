package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.service.AgentChatPreparationStrategy;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Agent 类型到前置处理策略的只读注册表。
 */
@Component
public class AgentChatPreparationStrategyRegistry {

    private final Map<AgentType, AgentChatPreparationStrategy> strategies;

    public AgentChatPreparationStrategyRegistry(List<AgentChatPreparationStrategy> strategies) {
        EnumMap<AgentType, AgentChatPreparationStrategy> registeredStrategies = new EnumMap<>(AgentType.class);
        for (AgentChatPreparationStrategy strategy : strategies) {
            AgentChatPreparationStrategy previous = registeredStrategies.putIfAbsent(strategy.supportedAgentType(), strategy);
            if (Objects.nonNull(previous)) {
                throw new IllegalStateException("Agent准备策略重复注册：" + strategy.supportedAgentType().getCode());
            }
        }
        this.strategies = Map.copyOf(registeredStrategies);
    }

    /**
     * 查找 Agent 类型对应策略；未知类型明确拒绝运行。
     */
    public AgentChatPreparationStrategy require(AgentType agentType) {
        AgentChatPreparationStrategy strategy = strategies.get(agentType);
        if (Objects.isNull(strategy)) {
            String typeCode = Optional.ofNullable(agentType).map(AgentType::getCode).orElse("null");
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前运行时不支持Agent类型：" + typeCode);
        }
        return strategy;
    }
}
