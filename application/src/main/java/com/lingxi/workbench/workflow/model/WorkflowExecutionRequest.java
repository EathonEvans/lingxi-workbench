package com.lingxi.workbench.workflow.model;

import java.util.Map;

/**
 * 单次工作流执行请求，封装执行标识、版本绑定和初始输入变量。
 *
 * @param executionId           本次执行的唯一标识，用于全链路追踪
 * @param applicationVersionId 发布版本 ID，用于锁定该次执行使用的所有 Agent 版本
 * @param inputs                工作流初始输入变量，key 不可以下划线开头
 */
public record WorkflowExecutionRequest(
        String executionId,
        String applicationVersionId,
        Map<String, Object> inputs) {

    public WorkflowExecutionRequest {
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
    }
}
