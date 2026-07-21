package com.lingxi.workbench.admin.prompt.model.vo;

import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;

/** 可插入Prompt正文的内置变量。 */
public record AvailablePromptVariableVO(
        /** 稳定参数Key。 */
        String name,
        /** 用于管理界面展示的中文名称。 */
        String displayName,
        /** 变量数据类型。 */
        PromptVariableType type,
        /** 变量用途说明。 */
        String description) {}
