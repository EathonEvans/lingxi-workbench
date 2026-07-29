package com.lingxi.workbench.admin.model.common.constants;

import java.util.Set;

/**
 * 模型实例扩展参数的稳定约束。
 *
 * <p>扩展参数只补充供应商特有字段，不允许覆盖平台已经结构化管理的请求参数，
 * 也不允许把鉴权信息绕过密钥管理写入 JSONB。</p>
 */
public interface ModelExtensionParameterConstants {

    /** 扩展参数序列化后的最大字符数。 */
    int MAX_JSON_LENGTH = 16 * 1024;

    /** 扩展参数允许的最大嵌套深度。 */
    int MAX_NESTING_DEPTH = 8;

    /** 平台负责构造、扩展参数禁止覆盖的顶层字段。 */
    Set<String> RESERVED_TOP_LEVEL_KEYS = Set.of(
            "model",
            "messages",
            "input",
            "stream",
            "stream_options",
            "temperature",
            "top_p",
            "max_tokens",
            "max_completion_tokens",
            "stop",
            "tools",
            "tool_choice",
            "parallel_tool_calls");

    /** 任意层级都禁止出现的敏感字段名称。 */
    Set<String> SENSITIVE_KEYS = Set.of(
            "api_key",
            "api-key",
            "apikey",
            "authorization",
            "access_token",
            "access-token",
            "password",
            "secret");
}
