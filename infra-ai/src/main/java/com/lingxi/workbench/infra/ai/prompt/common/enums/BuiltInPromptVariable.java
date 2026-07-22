package com.lingxi.workbench.infra.ai.prompt.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 平台内置Prompt变量目录。
 *
 * <p>这里只声明平台能够提供的变量契约，不负责读取登录用户、会话等业务数据。</p>
 */
@Getter
@RequiredArgsConstructor
public enum BuiltInPromptVariable {
    SYSTEM_CURRENT_TIME("system.current_time", "当前时间", PromptVariableDataType.DATETIME, "当前请求时间，包含时区"),
    SYSTEM_CURRENT_DATE("system.current_date", "当前日期", PromptVariableDataType.DATE, "当前请求日期"),
    SYSTEM_TIMEZONE("system.timezone", "系统时区", PromptVariableDataType.STRING, "系统使用的标准时区"),
    SYSTEM_LANGUAGE("system.language", "系统语言", PromptVariableDataType.STRING, "系统默认语言"),
    SYSTEM_PLATFORM_NAME("system.platform_name", "平台名称", PromptVariableDataType.STRING, "当前平台名称"),
    USER_ID("user.id", "用户ID", PromptVariableDataType.LONG, "当前登录用户主键"),
    USER_NAME("user.name", "用户名称", PromptVariableDataType.STRING, "当前用户显示名称"),
    USER_QUERY("user.query", "用户输入", PromptVariableDataType.STRING, "当前轮用户输入"),
    SESSION_ID("session.id", "会话ID", PromptVariableDataType.LONG, "当前会话主键"),
    SESSION_HISTORY("session.history", "会话历史", PromptVariableDataType.MESSAGE_LIST, "当前会话消息列表");

    /** 变量唯一编码，格式为 {@code <scope>.<name>}。 */
    private final String code;
    /** 变量在界面中展示的中文名称。 */
    private final String displayName;
    /** 变量的数据类型。 */
    private final PromptVariableDataType type;
    /** 变量的详细说明，用于帮助用户理解其含义。 */
    private final String describe;
}
