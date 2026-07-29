package com.lingxi.workbench.workflow.model.config;

import com.alibaba.fastjson2.annotation.JSONType;

/**
 * 开始节点配置。
 */
@JSONType(typeName = "START")
public record StartNodeConfig() implements WorkflowNodeConfig {
}
