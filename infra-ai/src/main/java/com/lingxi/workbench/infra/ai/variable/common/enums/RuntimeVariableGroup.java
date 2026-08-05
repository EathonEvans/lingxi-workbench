package com.lingxi.workbench.infra.ai.variable.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 运行时变量的一级业务分组。 */
@Getter
@RequiredArgsConstructor
public enum RuntimeVariableGroup {

    SYSTEM("system", "系统变量", 10),
    APP("app", "应用变量", 20),
    AGENT("agent", "Agent变量", 30),
    COLLABORATION("collaboration", "Agent协作", 35),
    USER("user", "用户变量", 40),
    INPUT("input", "输入变量", 50),
    SESSION("session", "会话变量", 60),
    QUERY("query", "问题处理", 70),
    WORKFLOW("workflow", "工作流变量", 80),
    NODE("node", "节点变量", 90),
    TOOLS("tools", "工具变量", 100),
    KNOWLEDGE("knowledge", "知识检索", 110);

    /** 稳定分组编码，也是变量完整编码的第一段。 */
    private final String code;
    /** 管理端展示名称。 */
    private final String displayName;
    /** 管理端展示顺序。 */
    private final int sort;
}
