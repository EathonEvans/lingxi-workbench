package com.lingxi.workbench.application.runtime.common.enums;

/**
 * 意图门控完成后交给上层执行调度器的动作指令。
 *
 * <p>该枚举由 {@code IntentRoutingServiceImpl} 返回，上层
 * {@code ApplicationWorkflowChatServiceImpl} 根据动作类型决定后续行为：</p>
 * <ul>
 *   <li><b>EXECUTE_WORKFLOW</b> — 意图明确且槽位完整，直接启动主 Workflow 执行。
 *       executionQuery 包含原始问题或拼接了已收集槽位的增强查询。</li>
 *   <li><b>EXECUTE_AGENT</b> — 意图绑定到独立 Agent（含 Supervisor），
 *       跳过 Workflow 直接调用 Agent 的 ReAct 循环。</li>
 *   <li><b>FIXED_RESPONSE</b> — 命中固定答复映射或兜底策略，
 *       直接返回预定义文本，不调用模型也不执行 Tool。</li>
 *   <li><b>CLARIFICATION</b> — 需要用户补充信息（歧义消解或补槽位）。
 *       此时 SSE 返回 clarification 事件，前端展示选项或追问。</li>
 * </ul>
 */
public enum IntentRoutingAction {

    /** 启动主 Workflow 执行 */
    EXECUTE_WORKFLOW,

    /** 直接调用指定 Agent */
    EXECUTE_AGENT,

    /** 返回固定答复文本 */
    FIXED_RESPONSE,

    /** 需要用户澄清或补充信息 */
    CLARIFICATION
}