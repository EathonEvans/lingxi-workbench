package com.lingxi.workbench.infra.ai.variable.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 运行时变量允许进入的消费通道。 */
@Getter
@RequiredArgsConstructor
public enum RuntimeVariableChannel {

    SYSTEM_PROMPT("SYSTEM_PROMPT", "系统提示词"),
    USER_PROMPT("USER_PROMPT", "用户提示词"),
    CONTEXTUALIZATION_PROMPT("CONTEXTUALIZATION_PROMPT", "问题重写提示词"),
    INTENT_DECISION_PROMPT("INTENT_DECISION_PROMPT", "意图决策提示词"),
    STRUCTURED_MESSAGE("STRUCTURED_MESSAGE", "结构化消息"),
    NODE_CONTEXT("NODE_CONTEXT", "节点上下文");

    /** 稳定通道编码。 */
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
