package com.lingxi.workbench.chat.common.enums;

/** 模型生成任务事件类型。 */
public enum ChatGenerationEventType {
    /** 模型生成的文本增量。 */
    CHUNK,
    /** 从数据库恢复的完整或部分回答快照。 */
    SNAPSHOT,
    /** 模型回答生成完成。 */
    COMPLETED,
    /** 模型调用失败。 */
    FAILED,
    /** 用户主动取消模型生成。 */
    CANCELLED
}
