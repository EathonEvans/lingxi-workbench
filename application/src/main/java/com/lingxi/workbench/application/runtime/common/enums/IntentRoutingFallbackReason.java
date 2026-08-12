package com.lingxi.workbench.application.runtime.common.enums;

/** 快速路由将请求交还 fallback Agent 时的稳定原因。 */
public enum IntentRoutingFallbackReason {
    NOT_MATCHED,
    AMBIGUOUS_ACROSS_CAPABILITIES,
    UNMAPPED_CANDIDATE,
    ROUTING_DISABLED
}
