package com.lingxi.workbench.admin.prompt.common.constants;

/** Prompt变量命名与模板占位符规则。 */
public final class PromptVariableConstants {

    /** 支持的变量命名空间正则；新增命名空间时只需维护此处。 */
    public static final String NAMESPACE_REGEXP =
            "(?:system|app|agent|user|input|session|workflow|node|tools|knowledge)";

    /** 变量路径片段正则。 */
    private static final String PATH_SEGMENT_REGEXP = "(?:\\.[a-z][a-z0-9_]*)+";

    /** 完整变量名称正则。 */
    public static final String VARIABLE_NAME_REGEXP =
            "^" + NAMESPACE_REGEXP + PATH_SEGMENT_REGEXP + "$";

    /** Prompt模板变量占位符正则，第一捕获组为完整变量名称。 */
    public static final String VARIABLE_PLACEHOLDER_REGEXP =
            "\\{\\{\\s*(" + NAMESPACE_REGEXP + PATH_SEGMENT_REGEXP + ")\\s*}}";

    private PromptVariableConstants() {}
}
