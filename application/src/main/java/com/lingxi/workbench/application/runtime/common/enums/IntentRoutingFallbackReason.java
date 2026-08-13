package com.lingxi.workbench.application.runtime.common.enums;

/** 快速路由将请求交还 fallback Agent 时的稳定原因；同时用于路由决策证据观测。 */
public enum IntentRoutingFallbackReason {
    /** 向量召回未通过阈值门控，未命中任何意图 */
    NOT_MATCHED,
    /** 歧义候选分属不同能力，无法安全直达 */
    AMBIGUOUS_ACROSS_CAPABILITIES,
    /** 命中意图但未发布执行映射 */
    UNMAPPED_CANDIDATE,
    /** Application 未启用快速路由 */
    ROUTING_DISABLED,
    /** 向量召回直达锁定能力（用于观测，不进入 fallback） */
    ROUTED_BY_VECTOR,
    /** 同能力内歧义，进入澄清会话（用于观测，不进入 fallback） */
    AMBIGUOUS_WITHIN_CAPABILITY
}
