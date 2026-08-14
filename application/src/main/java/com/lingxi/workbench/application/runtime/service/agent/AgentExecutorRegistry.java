package com.lingxi.workbench.application.runtime.service.agent;

import com.lingxi.workbench.application.runtime.service.agent.AgentExecutor;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentRole;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * AgentRole 到唯一执行器的启动期完整注册表。
 */
@Component
public class AgentExecutorRegistry {

    private final Map<AgentRole, AgentExecutor> executors;

    public AgentExecutorRegistry(List<AgentExecutor> executors) {
        EnumMap<AgentRole, AgentExecutor> registered = new EnumMap<>(AgentRole.class);
        for (AgentRole role : AgentRole.values()) {
            List<AgentExecutor> matches = executors.stream()
                    .filter(executor -> executor.supports(role))
                    .toList();
            if (matches.size() != 1) {
                throw new IllegalStateException(
                        "Agent类型必须且只能注册一个执行器：" + role.getCode() + "，实际=" + matches.size());
            }
            registered.put(role, matches.getFirst());
        }
        this.executors = Map.copyOf(registered);
    }

    public AgentExecutor require(AgentRole role) {
        AgentExecutor executor = executors.get(role);
        if (executor == null) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前运行时不支持Agent类型：" + role);
        }
        return executor;
    }
}
