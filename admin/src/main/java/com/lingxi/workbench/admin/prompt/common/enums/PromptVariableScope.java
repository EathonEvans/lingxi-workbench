package com.lingxi.workbench.admin.prompt.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Prompt变量作用域。 */
@Getter
@RequiredArgsConstructor
public enum PromptVariableScope {
    SYSTEM("SYSTEM", "系统"),
    APPLICATION("APPLICATION", "应用"),
    AGENT("AGENT", "Agent"),
    USER("USER", "用户"),
    INPUT("INPUT", "输入"),
    SESSION("SESSION", "会话"),
    WORKFLOW("WORKFLOW", "工作流"),
    NODE("NODE", "节点"),
    TOOL("TOOL", "工具"),
    KNOWLEDGE("KNOWLEDGE", "知识库");

    /** 稳定编码。 */
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
