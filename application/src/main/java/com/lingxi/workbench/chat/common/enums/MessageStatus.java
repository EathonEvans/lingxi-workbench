package com.lingxi.workbench.chat.common.enums;

/** 会话消息生成状态。 */
public enum MessageStatus {
    /** 模型正在生成回答。 */
    GENERATING,
    /** 消息已经完整生成。 */
    COMPLETED,
    /** 模型调用失败。 */
    FAILED,
    /** 用户主动取消生成。 */
    CANCELLED,
    /** 服务异常退出等原因造成生成中断。 */
    INTERRUPTED
}
