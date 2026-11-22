package com.lingxi.workbench.admin.model.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 对话模型可声明的能力。 */
@Getter
@RequiredArgsConstructor
public enum ModelCapability {
    /** 支持流式输出。 */
    STREAMING("STREAMING", "流式输出"),
    /** 支持工具调用。 */
    TOOL_CALLING("TOOL_CALLING", "工具调用"),
    /** 支持结构化输出。 */
    STRUCTURED_OUTPUT("STRUCTURED_OUTPUT", "结构化输出"),
    /** 支持图像等视觉输入。 */
    VISION("VISION", "视觉输入");

    /** 稳定编码。 */
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
