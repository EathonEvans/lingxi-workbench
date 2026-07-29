package com.lingxi.workbench.chat.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 单轮对话的知识召回状态。 */
@Getter
@RequiredArgsConstructor
public enum RetrievalStatus {

    SKIPPED("SKIPPED", "未绑定可用知识库"),
    SUCCESS("SUCCESS", "召回成功"),
    EMPTY("EMPTY", "召回完成但没有命中"),
    DEGRADED("DEGRADED", "召回失败并已降级");

    @EnumValue
    private final String code;

    private final String describe;
}
