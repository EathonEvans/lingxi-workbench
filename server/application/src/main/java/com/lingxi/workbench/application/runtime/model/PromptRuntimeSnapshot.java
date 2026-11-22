package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;

import java.util.List;
import java.util.Objects;

/**
 * AgentVersion 锁定的已发布 PromptVersion 运行快照。
 *
 * @param promptVersionId Prompt 版本主键
 * @param content         未渲染的模板正文
 * @param variables       随该版本发布的变量声明
 * @param role            Prompt 最终进入的模型消息角色
 */
public record PromptRuntimeSnapshot(
        Long promptVersionId,
        String content,
        List<PromptRuntimeVariableDefinition> variables,
        PromptRole role) {

    /** 构建不可变快照，避免处理链修改版本变量声明。 */
    public PromptRuntimeSnapshot {
        Objects.requireNonNull(promptVersionId, "promptVersionId不能为空");
        Objects.requireNonNull(content, "Prompt模板正文不能为空");
        Objects.requireNonNull(role, "Prompt角色不能为空");
        variables = List.copyOf(Objects.requireNonNullElse(
                variables,
                List.of()));
    }
}
