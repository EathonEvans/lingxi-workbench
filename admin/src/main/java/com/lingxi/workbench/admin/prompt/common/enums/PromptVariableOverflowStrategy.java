package com.lingxi.workbench.admin.prompt.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Prompt变量内容超限时的处理策略。 */
@Getter
@RequiredArgsConstructor
public enum PromptVariableOverflowStrategy {
    TRUNCATE("TRUNCATE", "截断"),
    SUMMARY("SUMMARY", "摘要"),
    REJECT("REJECT", "拒绝执行"),
    TOP_K("TOP_K", "保留前K项"),
    DROP_LOW_PRIORITY("DROP_LOW_PRIORITY", "丢弃低优先级变量");

    /** 稳定编码。 */
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
