package com.lingxi.workbench.chat.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 会话消息来源。 */
@Getter
@RequiredArgsConstructor
public enum MessageOrigin {

    /** 用户发起的正常对话轮次。 */
    CHAT("CHAT", "对话"),
    /** 定时触发器产出的主动轮次。 */
    TRIGGER("TRIGGER", "定时任务");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;

    /** 中文说明。 */
    private final String describe;
}
