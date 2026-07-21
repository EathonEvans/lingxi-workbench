package com.lingxi.workbench.admin.prompt.model.vo;

import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;

/** Prompt变量响应。 */
public record PromptVariableVO(
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
