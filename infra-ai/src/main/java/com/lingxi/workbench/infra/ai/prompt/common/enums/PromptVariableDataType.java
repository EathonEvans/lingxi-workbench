package com.lingxi.workbench.infra.ai.prompt.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Prompt运行时变量的数据类型。 */
@Getter
@RequiredArgsConstructor
public enum PromptVariableDataType {
    STRING("STRING", "字符串"),
    LONG("LONG", "长整数"),
    DATE("DATE", "日期"),
    DATETIME("DATETIME", "日期时间"),
    MESSAGE_LIST("MESSAGE_LIST", "消息列表");

    /** 数据类型稳定编码。 */
    private final String code;
    /** 数据类型中文说明。 */
    private final String describe;
}
