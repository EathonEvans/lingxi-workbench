package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.workflow.model.ApplicationDefinition;

import java.util.Objects;

/**
 * 当前 Application 生效版本的不可变运行快照。
 *
 * @param applicationId        应用主键
 * @param applicationCode      应用稳定编码
 * @param applicationVersionId 当前生效的版本主键
 * @param definition           版本中冻结的 Workflow 定义
 */
public record ApplicationRuntimeSnapshot(
        Long applicationId,
        String applicationCode,
        Long applicationVersionId,
        ApplicationDefinition definition) {

    /**
     * 快照一旦创建就必须是完整、可执行的已发布 Application。
     *
     * <p>这里校验的是运行模型自身的不变量，不能只依赖 Controller 参数校验：
     * 定时任务、消息消费或内部调用未来也可能创建运行快照。</p>
     */
    public ApplicationRuntimeSnapshot {
        Objects.requireNonNull(applicationId, "applicationId不能为空");
        Objects.requireNonNull(applicationVersionId, "applicationVersionId不能为空");
        Objects.requireNonNull(definition, "Application运行定义不能为空");
        Objects.requireNonNull(definition.workflow(), "Application Workflow不能为空");
        if (applicationCode == null || applicationCode.isBlank()) {
            throw new IllegalArgumentException("applicationCode不能为空");
        }
    }
}
