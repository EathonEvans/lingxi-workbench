package com.lingxi.workbench.admin.prompt.model.vo;

/** Prompt 变量响应。 */
public record PromptVariableVO(
        String name,
        boolean required,
        String description,
        String defaultValue) {}
