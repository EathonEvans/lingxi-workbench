package com.lingxi.workbench.admin.prompt.model.entity;

import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;

/**
 * Prompt版本内嵌的变量声明。
 *
 * <p>该对象随Prompt版本整体存储在JSONB字段中，不对应独立数据表。
 */
public record PromptVariableDefinition(
        /** 变量名称。 */
        String name,
        /** 变量数据类型。 */
        PromptVariableType type,
        /** 是否必填。 */
        boolean required,
        /** 变量说明。 */
        String description,
        /** 测试渲染示例值。 */
        Object example) {}
