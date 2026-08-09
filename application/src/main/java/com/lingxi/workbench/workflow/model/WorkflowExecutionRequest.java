package com.lingxi.workbench.workflow.model;

import cn.hutool.core.collection.CollUtil;

import java.util.Map;

/**
 * 单次工作流执行请求，封装执行标识、版本绑定和初始输入变量。
 *
 * @param executionId       上层执行标识，用于关联同一次 Chat 或 Planner 运行
 * @param workflowId        Workflow 主键
 * @param workflowVersionId 本次执行锁定的 WorkflowVersion 主键
 * @param inputs            工作流初始输入变量，key 不可以下划线开头
 */
public record WorkflowExecutionRequest(
        String executionId,
        Long workflowId,
        Long workflowVersionId,
        Map<String, Object> inputs) {

    public WorkflowExecutionRequest {
        inputs = CollUtil.isEmpty(inputs) ? Map.of() : Map.copyOf(inputs);
    }
}
