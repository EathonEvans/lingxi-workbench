package com.lingxi.workbench.admin.prompt.model.vo;

import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Prompt变量响应。
 *
 * @param name        变量名称
 * @param type        变量数据类型
 * @param required    是否必填
 * @param description 变量说明
 * @param example     测试渲染示例值
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptVariableVO {

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
