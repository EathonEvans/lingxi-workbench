package com.lingxi.workbench.application.runtime.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 触发器到期但未执行的原因。 */
@Getter
@RequiredArgsConstructor
public enum TriggerSkipReason {

    /** 落点在静默时段内，顺延到下个周期。 */
    QUIET_HOURS("QUIET_HOURS", "静默时段"),
    /** 专属会话仍有消息生成中。 */
    CONVERSATION_BUSY("CONVERSATION_BUSY", "会话忙"),
    /** 当日运行次数达到用户级配额。 */
    DAILY_QUOTA("DAILY_QUOTA", "当日配额已用尽"),
    /** 绑定的 Application 版本已不可用。 */
    VERSION_UNAVAILABLE("VERSION_UNAVAILABLE", "绑定版本不可用"),
    /** 专属会话仍有待人工决策的交互，等待用户处理后再触发。 */
    INTERACTION_PENDING("INTERACTION_PENDING", "存在待确认交互");

    /** 稳定编码，同时作为数据库存储值。 */
    @EnumValue
    private final String code;

    /** 中文说明。 */
    private final String describe;
}
