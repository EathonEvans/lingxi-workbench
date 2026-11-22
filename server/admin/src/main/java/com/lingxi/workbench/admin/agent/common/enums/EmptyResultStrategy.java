package com.lingxi.workbench.admin.agent.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 向量召回为空时的处理策略。 */
@Getter
@RequiredArgsConstructor
public enum EmptyResultStrategy {
    ANSWER_WITHOUT_CONTEXT("ANSWER_WITHOUT_CONTEXT", "无上下文继续回答"),
    RETURN_EMPTY_HINT("RETURN_EMPTY_HINT", "返回未检索到资料提示");

    private final String code;
    private final String describe;
}
