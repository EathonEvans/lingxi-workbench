package com.lingxi.workbench.infra.ai.variable.common.enums;

import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptVariableDataType;
import lombok.Getter;

import java.util.List;

/**
 * 平台内置运行时变量目录。
 *
 * <p>变量编码由分组编码和组内 Key 统一组成，不再重复维护完整字符串。
 * 消费通道描述变量可以进入 Prompt、结构化消息或节点上下文中的哪些位置。</p>
 */
@Getter
public enum BuiltInRuntimeVariable {

    SYSTEM_CURRENT_TIME(
            RuntimeVariableGroup.SYSTEM,
            "current_time",
            "当前时间",
            PromptVariableDataType.DATETIME,
            "当前请求时间，包含时区",
            RuntimeVariableProducer.REQUEST_INITIALIZATION,
            RuntimeVariableChannel.SYSTEM_PROMPT,
            RuntimeVariableChannel.USER_PROMPT,
            RuntimeVariableChannel.CONTEXTUALIZATION_PROMPT,
            RuntimeVariableChannel.INTENT_DECISION_PROMPT,
            RuntimeVariableChannel.NODE_CONTEXT),
    SYSTEM_CURRENT_DATE(
            RuntimeVariableGroup.SYSTEM,
            "current_date",
            "当前日期",
            PromptVariableDataType.DATE,
            "当前请求日期",
            RuntimeVariableProducer.REQUEST_INITIALIZATION,
            RuntimeVariableChannel.SYSTEM_PROMPT,
            RuntimeVariableChannel.USER_PROMPT,
            RuntimeVariableChannel.CONTEXTUALIZATION_PROMPT,
            RuntimeVariableChannel.INTENT_DECISION_PROMPT,
            RuntimeVariableChannel.NODE_CONTEXT),
    SYSTEM_TIMEZONE(
            RuntimeVariableGroup.SYSTEM,
            "timezone",
            "系统时区",
            PromptVariableDataType.STRING,
            "系统使用的标准时区",
            RuntimeVariableProducer.REQUEST_INITIALIZATION,
            RuntimeVariableChannel.SYSTEM_PROMPT,
            RuntimeVariableChannel.USER_PROMPT,
            RuntimeVariableChannel.CONTEXTUALIZATION_PROMPT,
            RuntimeVariableChannel.INTENT_DECISION_PROMPT,
            RuntimeVariableChannel.NODE_CONTEXT),
    SYSTEM_LANGUAGE(
            RuntimeVariableGroup.SYSTEM,
            "language",
            "系统语言",
            PromptVariableDataType.STRING,
            "系统默认语言",
            RuntimeVariableProducer.REQUEST_INITIALIZATION,
            RuntimeVariableChannel.SYSTEM_PROMPT,
            RuntimeVariableChannel.USER_PROMPT,
            RuntimeVariableChannel.CONTEXTUALIZATION_PROMPT,
            RuntimeVariableChannel.INTENT_DECISION_PROMPT,
            RuntimeVariableChannel.NODE_CONTEXT),
    SYSTEM_PLATFORM_NAME(
            RuntimeVariableGroup.SYSTEM,
            "platform_name",
            "平台名称",
            PromptVariableDataType.STRING,
            "当前平台名称",
            RuntimeVariableProducer.REQUEST_INITIALIZATION,
            RuntimeVariableChannel.SYSTEM_PROMPT,
            RuntimeVariableChannel.USER_PROMPT,
            RuntimeVariableChannel.CONTEXTUALIZATION_PROMPT,
            RuntimeVariableChannel.INTENT_DECISION_PROMPT,
            RuntimeVariableChannel.NODE_CONTEXT),
    COLLABORATION_MAX_DELEGATIONS(
            RuntimeVariableGroup.COLLABORATION,
            "max_delegations",
            "最大委派次数",
            PromptVariableDataType.INTEGER,
            "当前Application发布快照允许的最大成员Agent委派次数",
            RuntimeVariableProducer.APPLICATION_SNAPSHOT,
            RuntimeVariableChannel.SYSTEM_PROMPT,
            RuntimeVariableChannel.USER_PROMPT,
            RuntimeVariableChannel.NODE_CONTEXT),
    USER_ID(
            RuntimeVariableGroup.USER,
            "id",
            "用户ID",
            PromptVariableDataType.LONG,
            "当前登录用户主键",
            RuntimeVariableProducer.REQUEST_INITIALIZATION,
            RuntimeVariableChannel.SYSTEM_PROMPT,
            RuntimeVariableChannel.USER_PROMPT,
            RuntimeVariableChannel.CONTEXTUALIZATION_PROMPT,
            RuntimeVariableChannel.NODE_CONTEXT),
    USER_NAME(
            RuntimeVariableGroup.USER,
            "name",
            "用户名称",
            PromptVariableDataType.STRING,
            "当前用户显示名称",
            RuntimeVariableProducer.REQUEST_INITIALIZATION,
            RuntimeVariableChannel.USER_PROMPT,
            RuntimeVariableChannel.CONTEXTUALIZATION_PROMPT,
            RuntimeVariableChannel.NODE_CONTEXT),
    USER_QUERY(
            RuntimeVariableGroup.USER,
            "query",
            "用户输入",
            PromptVariableDataType.STRING,
            "当前轮用户输入",
            RuntimeVariableProducer.REQUEST_INITIALIZATION,
            RuntimeVariableChannel.USER_PROMPT,
            RuntimeVariableChannel.CONTEXTUALIZATION_PROMPT,
            RuntimeVariableChannel.NODE_CONTEXT),
    SESSION_ID(
            RuntimeVariableGroup.SESSION,
            "id",
            "会话ID",
            PromptVariableDataType.LONG,
            "当前会话主键",
            RuntimeVariableProducer.REQUEST_INITIALIZATION,
            RuntimeVariableChannel.SYSTEM_PROMPT,
            RuntimeVariableChannel.USER_PROMPT,
            RuntimeVariableChannel.CONTEXTUALIZATION_PROMPT,
            RuntimeVariableChannel.NODE_CONTEXT),
    SESSION_HISTORY(
            RuntimeVariableGroup.SESSION,
            "history",
            "会话历史",
            PromptVariableDataType.MESSAGE_LIST,
            "当前会话消息列表",
            RuntimeVariableProducer.CONVERSATION_HISTORY,
            RuntimeVariableChannel.STRUCTURED_MESSAGE,
            RuntimeVariableChannel.NODE_CONTEXT),
    SESSION_SUMMARY(
            RuntimeVariableGroup.SESSION,
            "summary",
            "会话摘要",
            PromptVariableDataType.STRING,
            "已持久化且完成覆盖范围去重的会话摘要",
            RuntimeVariableProducer.CONVERSATION_HISTORY,
            RuntimeVariableChannel.SYSTEM_PROMPT,
            RuntimeVariableChannel.USER_PROMPT,
            RuntimeVariableChannel.CONTEXTUALIZATION_PROMPT,
            RuntimeVariableChannel.NODE_CONTEXT),
    CONTEXTUALIZATION_HISTORY(
            RuntimeVariableGroup.QUERY,
            "contextualization_history",
            "问题重写历史",
            PromptVariableDataType.MESSAGE_LIST,
            "按照问题重写策略裁剪的会话消息列表",
            RuntimeVariableProducer.CONVERSATION_HISTORY,
            RuntimeVariableChannel.CONTEXTUALIZATION_PROMPT,
            RuntimeVariableChannel.STRUCTURED_MESSAGE,
            RuntimeVariableChannel.NODE_CONTEXT),
    LONG_TERM_MEMORY(
            RuntimeVariableGroup.SESSION,
            "long_term_memory",
            "长期记忆",
            PromptVariableDataType.STRING,
            "上下文准备阶段加载的用户画像与历史交互记录",
            RuntimeVariableProducer.CONVERSATION_HISTORY,
            RuntimeVariableChannel.SYSTEM_PROMPT,
            RuntimeVariableChannel.NODE_CONTEXT);

    private final RuntimeVariableGroup group;
    private final String key;
    private final String displayName;
    private final PromptVariableDataType type;
    private final String describe;
    private final RuntimeVariableProducer producer;
    private final List<RuntimeVariableChannel> channels;

    BuiltInRuntimeVariable(
            RuntimeVariableGroup group,
            String key,
            String displayName,
            PromptVariableDataType type,
            String describe,
            RuntimeVariableProducer producer,
            RuntimeVariableChannel... channels) {
        this.group = group;
        this.key = key;
        this.displayName = displayName;
        this.type = type;
        this.describe = describe;
        this.producer = producer;
        this.channels = List.of(channels);
    }

    /** 返回对外稳定的完整变量编码。 */
    public String getCode() {
        return group.getCode() + "." + key;
    }

    /** 判断变量是否允许进入指定的运行时消费通道。 */
    public boolean supportsChannel(RuntimeVariableChannel channel) {
        return channels.contains(channel);
    }
}
