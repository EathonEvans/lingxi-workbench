package com.lingxi.workbench.chat.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 模型生成任务事件类型。 */
@Getter
@RequiredArgsConstructor
public enum ChatGenerationEventType {
    /** 模型生成的文本增量。 */
    CHUNK("CHUNK", "文本增量"),
    /** Agent 工具执行状态。 */
    TOOL("TOOL", "工具状态"),
    /** 从数据库恢复的完整或部分回答快照。 */
    SNAPSHOT("SNAPSHOT", "回答快照"),
    /** 模型回答生成完成。 */
    COMPLETED("COMPLETED", "生成完成"),
    /** 模型调用失败。 */
    FAILED("FAILED", "生成失败"),
    /** 用户主动取消模型生成。 */
    CANCELLED("CANCELLED", "已取消");

    /** 稳定编码。 */
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
