package com.lingxi.workbench.workflow.model.config;

import com.lingxi.workbench.framework.json.annotation.JSONType;
import com.lingxi.workbench.workflow.model.ConditionOperator;

/**
 * 条件节点配置。
 *
 * @param variable      待读取的工作流变量
 * @param operator      比较操作
 * @param expectedValue 期望值
 */
@JSONType(typeName = "CONDITION")
public record ConditionNodeConfig(
        String variable,
        ConditionOperator operator,
        Object expectedValue) implements WorkflowNodeConfig {
}
