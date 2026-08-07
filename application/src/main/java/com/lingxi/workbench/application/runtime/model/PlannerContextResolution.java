package com.lingxi.workbench.application.runtime.model;

import com.lingxi.workbench.chat.model.ccontext.ConversationContext;

import java.util.Set;

/**
 * @param context  当前请求应使用的共享上下文
 * @param resumed  是否从上一轮Planner交互会话恢复
 * @param confirmedCapabilityCodes 本轮已确认的能力编码
 */
public record PlannerContextResolution(ConversationContext context, boolean resumed,
                                       Set<String> confirmedCapabilityCodes) {

    public PlannerContextResolution {
        confirmedCapabilityCodes = confirmedCapabilityCodes == null ? Set.of() : Set.copyOf(confirmedCapabilityCodes);
    }
}
