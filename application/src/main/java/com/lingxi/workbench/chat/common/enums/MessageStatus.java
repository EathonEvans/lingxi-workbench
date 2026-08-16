package com.lingxi.workbench.chat.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 会话消息生成状态。 */
@Getter
@RequiredArgsConstructor
public enum MessageStatus {
    /** 模型正在生成回答。 */
    GENERATING("GENERATING", "生成中"),
    /** 已保存运行检查点，等待用户决定。 */
    WAITING_HUMAN("WAITING_HUMAN", "等待确认"),
    /** 消息已经完整生成。 */
    COMPLETED("COMPLETED", "已完成"),
    /** 模型调用失败。 */
    FAILED("FAILED", "生成失败"),
    /** 用户主动取消生成。 */
    CANCELLED("CANCELLED", "已取消"),
    /** 服务异常退出等原因造成生成中断。 */
    INTERRUPTED("INTERRUPTED", "已中断");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
