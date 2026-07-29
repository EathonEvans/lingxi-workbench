package com.lingxi.workbench.infra.ai.prompt.common.constants;

/**
 * Prompt 变量命名和模板占位符的跨模块稳定契约。
 *
 * <p>管理端用它校验和预览模板，运行端用它提取并渲染已发布模板。
 * 两端必须复用同一规则，避免出现“管理端可以发布、运行端无法识别”的漂移。</p>
 */
public final class PromptVariableConstants {

    /**
     * 通用小写点路径正则。
     *
     * <p>正则只负责语法，不再维护业务命名空间白名单；内置变量是否存在由
     * RuntimeVariableCatalog 执行语义校验。</p>
     */
    private static final String VARIABLE_PATH_REGEXP =
            "[a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+";

    /** 完整变量名称正则。 */
    public static final String VARIABLE_NAME_REGEXP =
            "^" + VARIABLE_PATH_REGEXP + "$";

    /** Prompt 模板变量占位符正则，第一捕获组为完整变量名称。 */
    public static final String VARIABLE_PLACEHOLDER_REGEXP =
            "\\{\\{\\s*(" + VARIABLE_PATH_REGEXP + ")\\s*}}";

    private PromptVariableConstants() {
    }
}
