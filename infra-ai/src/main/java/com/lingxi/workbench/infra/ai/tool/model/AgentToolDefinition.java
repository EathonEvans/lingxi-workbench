package com.lingxi.workbench.infra.ai.tool.model;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

/**
 * 平台统一的结构化工具定义。
 *
 * <p>该对象描述管理端和模型调用共同关心的稳定元数据。参数 JSON Schema
 * 仍由 Spring AI 根据工具入参类型生成，避免项目重复实现协议细节。</p>
 */
@Getter
@EqualsAndHashCode
public final class AgentToolDefinition {

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

    @Builder
    private AgentToolDefinition(String code, String displayName, String description) {
        this.code = requireText(code, "工具编码");
        this.displayName = requireText(displayName, "工具展示名称");
        this.description = requireText(description, "工具描述");
    }

    private static String requireText(String value, String fieldName) {
        if (Objects.isNull(value) || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value;
    }
}
