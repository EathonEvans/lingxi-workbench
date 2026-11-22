package com.lingxi.workbench.infra.ai.tool.model;

import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionMode;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

/**
 * 平台统一的结构化工具定义。
 *
 * <p>该对象描述管理端和模型调用共同关心的稳定元数据。参数 JSON Schema
 * 由发布后 Tool 契约提供，AgentScope Toolkit 直接消费，不再经过 Spring AI Tool 转换。</p>
 */
@Getter
@EqualsAndHashCode
public final class AgentToolDefinition {

    private static final int PLATFORM_MAX_CALLS = 2;

    /**
     * 稳定工具编码，同时作为模型 Function name。
     */
    private final String code;
    /**
     * 面向管理端和运行事件的展示名称。
     */
    private final String displayName;
    /**
     * 提供给模型判断调用时机的工具描述。
     */
    private final String description;
    /**
     * 工具执行语义；未声明时按存在副作用处理，禁止自动复用结果。
     */
    private final AgentToolExecutionMode executionMode;
    /**
     * 单轮模型响应允许请求该工具的最大次数；小于等于零时使用平台上限。
     */
    private final int maxCallsPerRound;
    /**
     * 单次 Agent 执行允许调用该工具的最大次数；小于等于零时使用平台上限。
     */
    private final int maxCallsPerExecution;
    /** 独立于执行语义的安全与审批策略。 */
    private final ToolSecurityPolicy securityPolicy;
    /** 控制面 Tool 契约摘要，参数 Schema 或安全策略变化后必须变化。 */
    private final String configurationHash;

    @Builder
    private AgentToolDefinition(String code, String displayName, String description, AgentToolExecutionMode executionMode,
                                int maxCallsPerRound, int maxCallsPerExecution, ToolSecurityPolicy securityPolicy,
                                String configurationHash) {
        this.code = requireText(code, "工具编码");
        this.displayName = requireText(displayName, "工具展示名称");
        this.description = requireText(description, "工具描述");
        this.executionMode = Objects.requireNonNullElse(executionMode, AgentToolExecutionMode.SIDE_EFFECT);
        this.maxCallsPerRound = maxCallsPerRound;
        this.maxCallsPerExecution = maxCallsPerExecution;
        this.securityPolicy = Objects.requireNonNullElseGet(securityPolicy, ToolSecurityPolicy::defaults);
        this.configurationHash = Objects.requireNonNullElse(configurationHash, "");
    }

    /** 返回单轮生效上限。 */
    public int effectiveMaxCallsPerRound() {
        return effectiveLimit(maxCallsPerRound);
    }

    /** 返回单次执行生效上限。 */
    public int effectiveMaxCallsPerExecution() {
        return effectiveLimit(maxCallsPerExecution);
    }

    /** 平台统一限制每个工具最多调用两次，工具定义只允许进一步收紧。 */
    private static int effectiveLimit(int configuredLimit) {
        if (configuredLimit <= 0) {
            return PLATFORM_MAX_CALLS;
        }
        return Math.min(configuredLimit, PLATFORM_MAX_CALLS);
    }

    private static String requireText(String value, String fieldName) {
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
