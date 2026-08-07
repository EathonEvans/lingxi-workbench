package com.lingxi.workbench.application.runtime.model;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;

/**
 * @param capability    ApplicationVersion锁定的能力
 * @param inputs        已按计划引用解析的命名输入
 * @param context       当前可信执行上下文
 */
public record CapabilityExecutionRequest(
        ApplicationCapabilityDefinition capability,
        JSONObject inputs,
        ChatExecutionContext context) {
}
