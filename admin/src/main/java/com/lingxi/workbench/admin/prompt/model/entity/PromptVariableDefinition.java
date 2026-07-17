package com.lingxi.workbench.admin.prompt.model.entity;

import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableOverflowStrategy;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableScope;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableSource;
import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;

/**
 * Prompt版本内嵌的变量声明。
 *
 * <p>该对象随Prompt版本整体存储在JSONB字段中，不对应独立数据表。
 */
public record PromptVariableDefinition(
        /** 变量名称。 */
        String name,
        /** 变量展示名称。 */
        String displayName,
        /** 变量数据类型。 */
        PromptVariableType type,
        /** 变量作用域。 */
        PromptVariableScope scope,
        /** 变量来源。 */
        PromptVariableSource source,
        /** 是否必填。 */
        boolean required,
        /** 变量默认值。 */
        Object defaultValue,
        /** 变量说明。 */
        String description,
        /** 测试渲染示例值。 */
        Object example,
        /** 最大字符数。 */
        Integer maxLength,
        /** 数组最大元素数。 */
        Integer maxItems,
        /** Token预算裁剪优先级。 */
        Integer priority,
        /** 内容超限处理策略。 */
        PromptVariableOverflowStrategy overflowStrategy,
        /** 是否包含敏感信息。 */
        boolean sensitive,
        /** 是否允许最终用户编辑。 */
        boolean userEditable) {
}
