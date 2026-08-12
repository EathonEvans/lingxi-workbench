package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.application.runtime.service.agent.AgentExecutor;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** AgentType 到唯一执行器的启动期完整注册表。 */
@Component
public class AgentExecutorRegistry {

    private final Map<AgentType, AgentExecutor> executors;

    public AgentExecutorRegistry(List<AgentExecutor> executors) {
        EnumMap<AgentType, AgentExecutor> registered = new EnumMap<>(AgentType.class);
        for (AgentType agentType : AgentType.values()) {
            List<AgentExecutor> matches = executors.stream()
                    .filter(executor -> executor.supports(agentType))
                    .toList();
            if (matches.size() != 1) {
                throw new IllegalStateException(
                        "Agent类型必须且只能注册一个执行器：" + agentType.getCode() + "，实际=" + matches.size());
            }
            registered.put(agentType, matches.getFirst());
        }
        this.executors = Map.copyOf(registered);
    }

    public AgentExecutor require(AgentType agentType) {
        AgentExecutor executor = executors.get(agentType);
        if (executor == null) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前运行时不支持Agent类型：" + agentType);
        }
        return executor;
    }
}
