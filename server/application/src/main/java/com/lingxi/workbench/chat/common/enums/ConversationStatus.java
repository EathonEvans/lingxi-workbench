package com.lingxi.workbench.chat.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 会话状态。 */
@Getter
@RequiredArgsConstructor
public enum ConversationStatus {
    /** 正常使用中的会话。 */
    ACTIVE("ACTIVE", "使用中"),
    /** 已归档、仍可查看的会话。 */
    ARCHIVED("ARCHIVED", "已归档");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;
    /** 中文说明。 */
    private final String describe;
}
