package com.lingxi.workbench.admin.prompt.model.entity;

import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Prompt版本内嵌的变量声明。
 *
 * <p>该对象随Prompt版本整体存储在JSONB字段中，不对应独立数据表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptVariableDefinition {

    /**
     * 变量名称。
     */
    private String name;

    /**
     * 变量数据类型。
     */
    private PromptVariableType type;

    /**
     * 是否必填。
     */
    private boolean required;

    /**
     * 变量说明。
     */
    private String description;

    /**
     * 测试渲染示例值。
     */
    private Object example;
}
