package com.lingxi.workbench.admin.agent.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 向量检索异常时的处理策略。 */
@Getter
@RequiredArgsConstructor
public enum RetrievalFailureStrategy {
    FAIL_REQUEST("FAIL_REQUEST", "终止本次请求"),
    ANSWER_WITHOUT_CONTEXT("ANSWER_WITHOUT_CONTEXT", "降级为无上下文回答");

    private final String code;
    private final String describe;
}
