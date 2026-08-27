package com.lingxi.workbench.agentruntime.model;

import com.lingxi.workbench.agentruntime.checkpoint.AgentScopeCheckpoint;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** AgentScope Toolkit 的发布 Tool 快照、授权和细粒度调用计数。 */
public final class ExecutionTools {

    private final AgentToolRuntimeConfig runtimeConfig;
    private final Set<String> authorizedToolCodes;
    private final Map<String, AgentToolDefinition> definitions;
    private final Map<String, Integer> callCounts = new HashMap<>();
    private int callsInCurrentModelRound;

    public ExecutionTools(AgentToolRuntimeConfig runtimeConfig, List<String> authorizedToolCodes,
                          ExecutionPolicy policy) {
        this.runtimeConfig = runtimeConfig;
        this.authorizedToolCodes = Set.copyOf(Objects.requireNonNullElseGet(authorizedToolCodes, List::of));
        this.definitions = resolveDefinitions(runtimeConfig);
        if (!definitions.isEmpty() && (policy.getMaxModelCalls() < 2 || policy.getMaxToolCalls() < 1)) {
            throw new IllegalArgumentException("启用Tool时必须预留最终模型调用和Tool额度");
        }
    }

    public synchronized void restore(AgentScopeCheckpoint checkpoint) {
        List<AgentScopeCheckpoint.ToolCallCount> counts = checkpoint.getToolCallCounts() == null
                ? List.of() : checkpoint.getToolCallCounts();
        callCounts.clear();
        for (AgentScopeCheckpoint.ToolCallCount count : counts) {
            if (!definitions.containsKey(count.toolCode()) || count.calls() < 0) {
                throw new IllegalArgumentException("Checkpoint Tool调用计数不合法");
            }
            callCounts.put(count.toolCode(), count.calls());
        }
        int restoredRoundCalls = Objects.requireNonNullElse(checkpoint.getToolCallsInCurrentModelRound(), 0);
        if (restoredRoundCalls < 0) {
            throw new IllegalArgumentException("Checkpoint当前模型轮次Tool调用计数不合法");
        }
        callsInCurrentModelRound = restoredRoundCalls;
    }

    public synchronized void startModelRound() {
        callsInCurrentModelRound = 0;
    }

    /** 校验 AgentScope 请求的定义并同时占用全局、单 Tool、单轮三层预算。 */
    public synchronized void reserve(AgentToolDefinition definition, ExecutionBudget budget) {
        AgentToolDefinition authorized = requireDefinition(definition.getCode());
        if (!Objects.equals(authorized, definition)) {
            throw new IllegalArgumentException("AgentScope请求了未授权Tool定义：" + definition.getCode());
        }
        int usedByTool = callCounts.getOrDefault(definition.getCode(), 0);
        if (usedByTool >= definition.effectiveMaxCallsPerExecution()
                || callsInCurrentModelRound >= definition.effectiveMaxCallsPerRound()) {
            budget.requestFinalization("工具调用已达到平台收敛边界");
            throw new AgentRuntimeLimitException("AGENT_TOOL_CALL_LIMIT", "Agent工具调用次数已达到上限");
        }
        budget.reserveToolCall("工具调用已达到平台收敛边界");
        callsInCurrentModelRound++;
        callCounts.merge(definition.getCode(), 1, Integer::sum);
    }

    public void validateSkillTools(List<String> effectiveToolCodes, String skillCode) {
        if (!authorizedToolCodes.containsAll(Objects.requireNonNullElse(effectiveToolCodes, List.of()))) {
            throw new IllegalArgumentException("Skill有效Tool超出Agent授权范围：" + skillCode);
        }
    }

    public AgentToolDefinition requireDefinition(String toolCode) {
        AgentToolDefinition definition = definitions.get(toolCode);
        if (definition == null) {
            throw new IllegalArgumentException("Checkpoint请求了未授权Tool：" + toolCode);
        }
        return definition;
    }

    public boolean hasTools() {
        return !definitions.isEmpty();
    }

    public AgentToolRuntimeConfig runtimeConfig() {
        if (runtimeConfig == null) {
            throw new IllegalStateException("当前Agent调用未启用Tool");
        }
        return runtimeConfig;
    }

    public synchronized List<AgentScopeCheckpoint.ToolCallCount> checkpointCallCounts() {
        return callCounts.entrySet().stream()
                .map(entry -> new AgentScopeCheckpoint.ToolCallCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<String> checkpointAuthorizedToolCodes() {
        return authorizedToolCodes.stream().sorted().toList();
    }

    public synchronized int callsInCurrentModelRound() {
        return callsInCurrentModelRound;
    }

    private static Map<String, AgentToolDefinition> resolveDefinitions(AgentToolRuntimeConfig config) {
        if (config == null || !config.enabled()) {
            return Map.of();
        }
        return config.getTools().stream().map(AgentTool::definition)
                .collect(Collectors.toUnmodifiableMap(AgentToolDefinition::getCode, Function.identity()));
    }
}
