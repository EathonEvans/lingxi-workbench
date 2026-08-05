package com.lingxi.workbench.application.runtime.common.enums;

/**
 * 意图门控完成后交给执行层的动作。
 */
public enum IntentRoutingAction {
    /**
     * 执行工作流
     */
    EXECUTE_WORKFLOW,
    /**
     * 执行 Agent
     */
    EXECUTE_AGENT,
    /**
     * 固定回复
     */
    FIXED_RESPONSE,
    /**
     * 意图澄清
     */
    CLARIFICATION
}
