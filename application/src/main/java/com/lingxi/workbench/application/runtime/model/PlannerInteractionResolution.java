package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;

import java.util.Set;

/**
 * @param originalQuery 触发交互会话的原始任务
 * @param response      用户本次补充或确认的值
 * @param type          被恢复的交互会话类型
 * @param confirmedCapabilityCodes 本次明确确认的能力编码
 */
public record PlannerInteractionResolution(String originalQuery, String response, InteractionSessionType type,
                                            Set<String> confirmedCapabilityCodes) {

    public PlannerInteractionResolution {
        confirmedCapabilityCodes = confirmedCapabilityCodes == null ? Set.of() : Set.copyOf(confirmedCapabilityCodes);
    }
}
