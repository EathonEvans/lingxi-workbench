package com.lingxi.workbench.admin.prompt.model.vo;

import com.lingxi.workbench.admin.prompt.common.enums.PromptVariableType;

/**
 * 可插入Prompt正文的内置变量。
 *
 * @param name        稳定参数 Key
 * @param displayName 用于管理界面展示的中文名称
 * @param type        变量数据类型
 * @param description 变量用途说明
 */
public record AvailablePromptVariableVO(

        String name,

        String displayName,

        PromptVariableType type,

        String description) {}
