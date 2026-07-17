package com.lingxi.workbench.admin.prompt.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Prompt变量来源。 */
@Getter
@RequiredArgsConstructor
public enum PromptVariableSource {
    SYSTEM("SYSTEM", "系统注入"),
    APPLICATION_CONFIG("APPLICATION_CONFIG", "应用配置"),
    AGENT_CONFIG("AGENT_CONFIG", "Agent配置"),
    USER_INPUT("USER_INPUT", "用户或调用方输入"),
    SESSION_STATE("SESSION_STATE", "会话状态"),
    WORKFLOW_STATE("WORKFLOW_STATE", "工作流状态"),
    NODE_STATE("NODE_STATE", "节点状态"),
    TOOL_RESULT("TOOL_RESULT", "工具结果"),
    KNOWLEDGE_RESULT("KNOWLEDGE_RESULT", "知识库检索结果"),
    MANUAL("MANUAL", "手工测试数据");

    /** 稳定编码。 */
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
