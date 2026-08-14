package com.lingxi.workbench.agentexecute.loop;

import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsageAccumulator;
import com.lingxi.workbench.infra.ai.tool.AgentTool;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolDefinition;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolRuntimeConfig;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 单次 ReAct 回答的循环状态，统一维护调用次数、Token 用量和工具执行账本。
 */
public final class AgentLoopState {

    private final AgentToolRuntimeConfig runtimeConfig;
    private final TokenUsageAccumulator tokenUsage = new TokenUsageAccumulator();
    private final AgentToolExecutionLedger toolLedger = new AgentToolExecutionLedger();
    private final Map<String, AgentToolDefinition> toolDefinitions;
    private final Map<String, Integer> toolCallCounts = new HashMap<>();
    private int completedModelCalls;
    private int completedToolCalls;
    private boolean finalResponseRequested;
    private String finalizationReason;

    public AgentLoopState(AgentToolRuntimeConfig runtimeConfig) {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "工具运行配置不能为空");
        this.toolDefinitions = runtimeConfig.getTools().stream()
                .map(AgentTool::definition)
                .collect(Collectors.toUnmodifiableMap(AgentToolDefinition::getCode, Function.identity()));
    }

    /** 工作模型调用前校验软边界并占用一次调用额度。 */
    public void beginModelCall() {
        if (completedModelCalls >= runtimeConfig.getMaxModelCalls() - 1) {
            throw new AgentLoopLimitException("AGENT_MODEL_CALL_LIMIT", "处理步骤已达到上限，请缩小问题范围后重试",
                    "Agent工作模型额度已用尽，已保留最终回答调用，limit=" + runtimeConfig.getMaxModelCalls());
        }
        TokenUsage currentUsage = tokenUsage.snapshot();
        if (hasReached(currentUsage.inputTokens(), runtimeConfig.getMaxInputTokens())) {
            throw new AgentLoopLimitException("AGENT_INPUT_TOKEN_LIMIT", "本次处理的信息量已达到上限，请缩小问题范围后重试",
                    "Agent累计输入Token超过限制，limit=" + runtimeConfig.getMaxInputTokens());
        }
        if (hasReached(currentUsage.outputTokens(), runtimeConfig.getMaxOutputTokens())) {
            throw new AgentLoopLimitException("AGENT_OUTPUT_TOKEN_LIMIT", "本次回答已达到生成上限，请拆分问题后重试",
                    "Agent累计输出Token超过限制，limit=" + runtimeConfig.getMaxOutputTokens());
        }
        if (hasReached(currentUsage.totalTokens(), runtimeConfig.getMaxTotalTokens())) {
            throw new AgentLoopLimitException("AGENT_TOTAL_TOKEN_LIMIT", "本次处理的信息量已达到上限，正在整理已有结果",
                    "Agent累计Token达到工作软上限，limit=" + runtimeConfig.getMaxTotalTokens());
        }
        runtimeConfig.getExecutionBudget().reserveModelCall();
        completedModelCalls++;
    }

    /** 禁用工具的最终回答调用只校验硬边界，并使用Application预留额度。 */
    public void beginFinalModelCall() {
        if (completedModelCalls >= runtimeConfig.getMaxModelCalls()) {
            throw new AgentLoopLimitException("AGENT_MODEL_CALL_LIMIT", "本次处理已达到模型调用硬上限",
                    "Agent最终回答没有可用模型调用额度，limit=" + runtimeConfig.getMaxModelCalls());
        }
        TokenUsage currentUsage = tokenUsage.snapshot();
        if (hasReached(currentUsage.totalTokens(), runtimeConfig.getMaxFinalizationTokens())) {
            throw new AgentLoopLimitException("AGENT_TOTAL_TOKEN_HARD_LIMIT", "本次处理已达到Token硬上限",
                    "Agent累计Token达到最终回答硬上限，limit=" + runtimeConfig.getMaxFinalizationTokens());
        }
        runtimeConfig.getExecutionBudget().reserveFinalModelCall();
        completedModelCalls++;
    }

    /** 记录一轮模型调用的真实 Token 用量。 */
    public void recordUsage(TokenUsage usage) {
        tokenUsage.add(usage);
    }

    /**
     * 按全局、单轮和单工具执行上限筛选本轮允许执行的工具调用。
     *
     * <p>供应商即使忽略 parallelToolCalls=false 并一次返回大量 tool_calls，平台也只执行
     * 获批子集；截断后强制进入禁用工具的最终回答，避免下一轮继续扩散。</p>
     */
    public synchronized ToolCallSelection selectToolCalls(List<AssistantMessage.ToolCall> requestedCalls) {
        List<AssistantMessage.ToolCall> calls = List.copyOf(
                Objects.requireNonNull(requestedCalls, "模型工具调用列表不能为空"));
        List<AssistantMessage.ToolCall> approved = new ArrayList<>();
        Map<String, Integer> roundCounts = new HashMap<>();
        String limitReason = null;

        for (AssistantMessage.ToolCall call : calls) {
            AgentToolDefinition definition = toolDefinitions.get(call.name());
            if (Objects.isNull(definition)) {
                limitReason = "模型请求了未授权工具";
                continue;
            }
            int roundCount = roundCounts.getOrDefault(call.name(), 0);
            int executionCount = toolCallCounts.getOrDefault(call.name(), 0);
            boolean globalLimitReached = completedToolCalls + approved.size() >= runtimeConfig.getMaxToolCalls();
            boolean roundLimitReached = roundCount >= definition.effectiveMaxCallsPerRound();
            boolean executionLimitReached = executionCount >= definition.effectiveMaxCallsPerExecution();
            if (globalLimitReached || roundLimitReached || executionLimitReached) {
                limitReason = "工具调用已达到平台收敛边界";
                continue;
            }
            approved.add(call);
            roundCounts.merge(call.name(), 1, Integer::sum);
            toolCallCounts.merge(call.name(), 1, Integer::sum);
        }

        if (!approved.isEmpty()) {
            runtimeConfig.getExecutionBudget().reserveToolCalls(approved.size());
            completedToolCalls += approved.size();
        }
        boolean globalLimitReached = runtimeConfig.getMaxToolCalls() > 0
                && completedToolCalls >= runtimeConfig.getMaxToolCalls();
        boolean limited = approved.size() < calls.size() || globalLimitReached
                || hasReachedToolExecutionLimit(approved);
        if (limited) {
            requestFinalResponse(Objects.requireNonNullElse(limitReason, "工具已达到单次执行上限"));
        }
        return new ToolCallSelection(List.copyOf(approved), limited, finalizationReason());
    }

    private boolean hasReachedToolExecutionLimit(List<AssistantMessage.ToolCall> approved) {
        return approved.stream().map(AssistantMessage.ToolCall::name).distinct().anyMatch(toolCode -> {
            AgentToolDefinition definition = toolDefinitions.get(toolCode);
            int limit = definition.effectiveMaxCallsPerExecution();
            return limit < Integer.MAX_VALUE && toolCallCounts.getOrDefault(toolCode, 0) >= limit;
        });
    }

    AgentToolExecutionLedger toolLedger() {
        return toolLedger;
    }

    /** 收敛条件命中后只允许再进行一次禁用工具的总结调用。 */
    void requestFinalResponse(String reason) {
        finalResponseRequested = true;
        if (Objects.isNull(finalizationReason) || finalizationReason.isBlank()) {
            finalizationReason = reason;
        }
    }

    /** 判断下一轮是否必须禁用工具并收敛为最终回答。 */
    public boolean shouldFinalize() {
        return finalResponseRequested || completedModelCalls >= runtimeConfig.getMaxModelCalls() - 1
                || hasReached(tokenUsage.snapshot().totalTokens(), runtimeConfig.getMaxTotalTokens());
    }

    /** Token软上限命中后不再执行模型刚请求的新工具，避免继续扩大上下文。 */
    public boolean shouldFinalizeBeforeToolExecution() {
        return hasReached(tokenUsage.snapshot().totalTokens(), runtimeConfig.getMaxTotalTokens());
    }

    /** 返回本次 Agent 已完成的模型调用次数。 */
    public int completedModelCalls() {
        return completedModelCalls;
    }

    /** 返回本次 Agent 已获准执行的工具调用次数。 */
    public int completedToolCalls() {
        return completedToolCalls;
    }

    /** 返回首个触发收敛的安全原因。 */
    public String finalizationReason() {
        return Optional.ofNullable(finalizationReason).filter(reason -> !reason.isBlank())
                .orElse("运行预算已到边界或工具结果已经收敛");
    }

    private static boolean hasReached(Integer usedTokens, int limit) {
        return limit > 0 && Objects.nonNull(usedTokens) && usedTokens >= limit;
    }

    /**
     * @param approvedCalls 本轮获准执行的工具调用
     * @param limited       是否因平台边界截断或命中上限
     * @param reason        进入最终回答时的安全原因
     */
    public record ToolCallSelection(List<AssistantMessage.ToolCall> approvedCalls, boolean limited, String reason) {
    }
}
