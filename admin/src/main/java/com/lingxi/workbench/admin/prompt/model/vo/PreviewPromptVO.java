package com.lingxi.workbench.admin.prompt.model.vo;

/**
 * Prompt渲染预览响应。
 *
 * @param renderedPrompt 变量替换后的渲染结果
 */
public record PreviewPromptVO(
        /** 变量替换后的渲染结果。 */
        String renderedPrompt) {}
