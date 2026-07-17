package com.lingxi.workbench.admin.prompt.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Prompt变量数据类型。 */
@Getter
@RequiredArgsConstructor
public enum PromptVariableType {
    STRING("STRING", "字符串"),
    INTEGER("INTEGER", "整数"),
    LONG("LONG", "长整数"),
    DECIMAL("DECIMAL", "小数"),
    BOOLEAN("BOOLEAN", "布尔值"),
    DATE("DATE", "日期"),
    DATETIME("DATETIME", "日期时间"),
    OBJECT("OBJECT", "对象"),
    ARRAY("ARRAY", "数组"),
    DOCUMENT("DOCUMENT", "文档"),
    MESSAGE_LIST("MESSAGE_LIST", "消息列表");

    /** 稳定编码。 */
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
