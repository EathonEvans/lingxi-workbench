package com.lingxi.workbench.infra.ai.prompt.common;

import com.lingxi.workbench.infra.ai.prompt.common.constants.PromptVariableConstants;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Prompt 模板变量占位符语法检查。 */
public final class PromptTemplateSyntax {

    private static final Pattern VARIABLE_PATTERN =
            Pattern.compile(PromptVariableConstants.VARIABLE_PLACEHOLDER_REGEXP);

    private PromptTemplateSyntax() {
    }

    /**
     * 判断模板是否包含无法识别的变量起始标记。
     *
     * <p>模板语言只保留 {@code {{variable.path}}} 起始标记。单独出现的 {@code }}
     * 可能来自嵌套 JSON 的正常闭合，不能据此判定变量格式错误。</p>
     *
     * @param content Prompt 模板正文
     * @return 存在畸形变量占位符时返回 true
     */
    public static boolean hasInvalidVariable(String content) {
        if (content == null) {
            return true;
        }
        int searchFrom = 0;
        int variableStart;
        while ((variableStart = content.indexOf("{{", searchFrom)) >= 0) {
            Matcher matcher = VARIABLE_PATTERN.matcher(content);
            matcher.region(variableStart, content.length());
            if (!matcher.lookingAt()) {
                return true;
            }
            searchFrom = matcher.end();
        }
        return false;
    }
}
