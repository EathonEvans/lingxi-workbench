package com.lingxi.workbench.chat.model.ccontext;

import com.alibaba.fastjson2.JSON;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatHistoryMessage;
import com.lingxi.workbench.infra.ai.variable.common.enums.BuiltInRuntimeVariable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * 平台内置运行时变量的类型安全访问器目录。
 *
 * <p>每个变量只在这里定义一次读取类型和渲染方式。Prompt 模板按变量编码
 * 动态取值时也从同一目录查找，不再维护额外的 switch 分支。</p>
 */
public final class RuntimeVariableAccessors {

    /**
     * 当前请求时间，包含时区。
     */
    public static final RuntimeVariableAccessor<String> SYSTEM_CURRENT_TIME =
            stringAccessor(BuiltInRuntimeVariable.SYSTEM_CURRENT_TIME);
    /**
     * 当前请求日期。
     */
    public static final RuntimeVariableAccessor<String> SYSTEM_CURRENT_DATE =
            stringAccessor(BuiltInRuntimeVariable.SYSTEM_CURRENT_DATE);
    /**
     * 当前执行时区。
     */
    public static final RuntimeVariableAccessor<String> SYSTEM_TIMEZONE =
            stringAccessor(BuiltInRuntimeVariable.SYSTEM_TIMEZONE);
    /**
     * 当前执行语言。
     */
    public static final RuntimeVariableAccessor<String> SYSTEM_LANGUAGE =
            stringAccessor(BuiltInRuntimeVariable.SYSTEM_LANGUAGE);
    /**
     * 当前平台名称。
     */
    public static final RuntimeVariableAccessor<String> SYSTEM_PLATFORM_NAME =
            stringAccessor(BuiltInRuntimeVariable.SYSTEM_PLATFORM_NAME);
    /**
     * 当前Application发布快照允许的最大成员Agent委派次数。
     */
    public static final RuntimeVariableAccessor<Integer> COLLABORATION_MAX_DELEGATIONS =
            integerAccessor(BuiltInRuntimeVariable.COLLABORATION_MAX_DELEGATIONS);
    /**
     * 当前登录用户主键。
     */
    public static final RuntimeVariableAccessor<Long> USER_ID =
            longAccessor(BuiltInRuntimeVariable.USER_ID);
    /**
     * 当前登录用户显示名称。
     */
    public static final RuntimeVariableAccessor<String> USER_NAME =
            stringAccessor(BuiltInRuntimeVariable.USER_NAME);
    /**
     * 用户本轮提交的原始问题。
     */
    public static final RuntimeVariableAccessor<String> USER_QUERY =
            stringAccessor(BuiltInRuntimeVariable.USER_QUERY);
    /**
     * 当前会话主键。
     */
    public static final RuntimeVariableAccessor<Long> SESSION_ID =
            longAccessor(BuiltInRuntimeVariable.SESSION_ID);
    /**
     * 历史节点裁剪后的模型消息列表。
     */
    public static final RuntimeVariableAccessor<List<ChatHistoryMessage>> SESSION_HISTORY =
            historyAccessor(BuiltInRuntimeVariable.SESSION_HISTORY);
    /**
     * 已持久化的会话摘要。
     */
    public static final RuntimeVariableAccessor<String> SESSION_SUMMARY =
            stringAccessor(BuiltInRuntimeVariable.SESSION_SUMMARY);
    /**
     * 按问题改写策略独立裁剪后的历史消息。
     */
    public static final RuntimeVariableAccessor<List<ChatHistoryMessage>> QUERY_REWRITE_HISTORY =
            historyAccessor(BuiltInRuntimeVariable.QUERY_REWRITE_HISTORY);
    /**
     * 问题改写节点产出的实际向量检索问题。
     */
    public static final RuntimeVariableAccessor<String> KNOWLEDGE_RETRIEVAL_QUERY =
            stringAccessor(BuiltInRuntimeVariable.KNOWLEDGE_RETRIEVAL_QUERY);
    /**
     * 经过编号和 Token 预算处理后的知识库上下文。
     */
    public static final RuntimeVariableAccessor<String> KNOWLEDGE_CONTEXT =
            stringAccessor(BuiltInRuntimeVariable.KNOWLEDGE_CONTEXT);
    /**
     * 长期记忆上下文，由 MemoryInjectionNode 注入。
     */
    public static final RuntimeVariableAccessor<String> LONG_TERM_MEMORY =
            stringAccessor(BuiltInRuntimeVariable.LONG_TERM_MEMORY);

    private static final EnumMap<BuiltInRuntimeVariable, RuntimeVariableAccessor<?>>
            ACCESSORS = buildAccessors();

    private RuntimeVariableAccessors() {
    }

    /**
     * 按变量目录定义查找类型安全访问器。
     *
     * @param variable 内置变量定义
     * @return 对应的运行时访问器
     */
    static RuntimeVariableAccessor<?> require(
            BuiltInRuntimeVariable variable) {
        RuntimeVariableAccessor<?> accessor = ACCESSORS.get(variable);
        if (accessor == null) {
            throw new BusinessException(
                    HttpStatusEnum.CONFLICT,
                    "运行时变量缺少访问器：" + variable.getCode());
        }
        return accessor;
    }

    private static EnumMap<BuiltInRuntimeVariable, RuntimeVariableAccessor<?>>
    buildAccessors() {
        EnumMap<BuiltInRuntimeVariable, RuntimeVariableAccessor<?>> accessors = new EnumMap<>(BuiltInRuntimeVariable.class);
        register(accessors, SYSTEM_CURRENT_TIME);
        register(accessors, SYSTEM_CURRENT_DATE);
        register(accessors, SYSTEM_TIMEZONE);
        register(accessors, SYSTEM_LANGUAGE);
        register(accessors, SYSTEM_PLATFORM_NAME);
        register(accessors, COLLABORATION_MAX_DELEGATIONS);
        register(accessors, USER_ID);
        register(accessors, USER_NAME);
        register(accessors, USER_QUERY);
        register(accessors, SESSION_ID);
        register(accessors, SESSION_HISTORY);
        register(accessors, SESSION_SUMMARY);
        register(accessors, QUERY_REWRITE_HISTORY);
        register(accessors, KNOWLEDGE_RETRIEVAL_QUERY);
        register(accessors, KNOWLEDGE_CONTEXT);
        register(accessors, LONG_TERM_MEMORY);
        return accessors;
    }

    private static void register(
            EnumMap<BuiltInRuntimeVariable, RuntimeVariableAccessor<?>> accessors,
            RuntimeVariableAccessor<?> accessor) {
        RuntimeVariableAccessor<?> previous =
                accessors.putIfAbsent(accessor.variable(), accessor);
        if (previous != null) {
            throw new IllegalStateException(
                    "运行时变量访问器重复定义：" + accessor.variable().getCode());
        }
    }

    private static RuntimeVariableAccessor<String> stringAccessor(
            BuiltInRuntimeVariable variable) {
        return new RuntimeVariableAccessor<>(
                variable,
                value -> readValue(variable, value, String.class),
                value -> value);
    }

    private static RuntimeVariableAccessor<Long> longAccessor(
            BuiltInRuntimeVariable variable) {
        return new RuntimeVariableAccessor<>(
                variable,
                value -> readValue(variable, value, Long.class),
                String::valueOf);
    }

    private static RuntimeVariableAccessor<Integer> integerAccessor(
            BuiltInRuntimeVariable variable) {
        return new RuntimeVariableAccessor<>(
                variable,
                value -> readValue(variable, value, Integer.class),
                String::valueOf);
    }

    private static <T> T readValue(
            BuiltInRuntimeVariable variable,
            Object value,
            Class<T> expectedType) {
        if (!expectedType.isInstance(value)) {
            throw invalidType(variable, expectedType.getSimpleName());
        }
        return expectedType.cast(value);
    }

    private static RuntimeVariableAccessor<List<ChatHistoryMessage>> historyAccessor(
            BuiltInRuntimeVariable variable) {
        return new RuntimeVariableAccessor<>(
                variable,
                value -> readHistoryMessages(variable, value),
                JSON::toJSONString);
    }

    private static List<ChatHistoryMessage> readHistoryMessages(
            BuiltInRuntimeVariable variable,
            Object value) {
        if (!(value instanceof List<?> values)) {
            throw invalidType(variable, "List<ChatHistoryMessage>");
        }
        List<ChatHistoryMessage> messages = new ArrayList<>(values.size());
        for (Object item : values) {
            if (!(item instanceof ChatHistoryMessage message)) {
                throw invalidType(variable, "List<ChatHistoryMessage>");
            }
            messages.add(message);
        }
        return List.copyOf(messages);
    }

    private static BusinessException invalidType(
            BuiltInRuntimeVariable variable,
            String expectedType) {
        return new BusinessException(
                HttpStatusEnum.CONFLICT,
                "运行时变量类型错误："
                        + variable.getCode()
                        + "，期望类型="
                        + expectedType);
    }
}
