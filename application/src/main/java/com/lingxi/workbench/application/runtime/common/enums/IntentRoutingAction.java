package com.lingxi.workbench.application.runtime.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 意图门控完成后交给上层执行调度器的动作指令。
 *
 * <p>该枚举由 {@code IntentRoutingServiceImpl} 返回，上层
 * {@code ApplicationChatExecutionServiceImpl} 根据动作类型决定后续行为：</p>
 * <ul>
 *   <li><b>EXECUTE_WORKFLOW</b> — 意图明确且槽位完整，执行叶子映射锁定的 WorkflowVersion。
 *       executionQuery 包含原始问题或拼接了已收集槽位的增强查询。</li>
 *   <li><b>EXECUTE_AGENT</b> — 意图绑定到独立 Agent（含 Supervisor），
 *       跳过 Workflow 直接调用 Agent 的 ReAct 循环。</li>
 *   <li><b>FIXED_RESPONSE</b> — 命中固定答复映射或兜底策略，
 *       直接返回预定义文本，不调用模型也不执行 Tool。</li>
 *   <li><b>CLARIFICATION</b> — 需要用户补充信息（歧义消解或补槽位）。
 *       此时 SSE 返回 clarification 事件，前端展示选项或追问。</li>
 *   <li><b>DEFER_TO_AGENT</b> — 意图路由未形成安全直达结果，交由 fallback Agent。</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum IntentRoutingAction {

    /** 当前 Application 未启用意图路由，或正在恢复运行时交互。 */
    SKIPPED("SKIPPED", "跳过意图路由"),

    /** 执行叶子映射锁定的 WorkflowVersion */
    EXECUTE_WORKFLOW("EXECUTE_WORKFLOW", "执行Workflow"),

    /** 直接调用指定 Agent */
    EXECUTE_AGENT("EXECUTE_AGENT", "执行ReAct Agent"),

    /** 返回固定答复文本 */
    FIXED_RESPONSE("FIXED_RESPONSE", "返回固定答复"),

    /** 需要用户澄清或补充信息 */
    CLARIFICATION("CLARIFICATION", "请求用户澄清"),

    /** 跨能力歧义或候选未映射，携带安全证据交由fallback Agent判断。 */
    DEFER_TO_AGENT("DEFER_TO_AGENT", "交由fallback Agent判断");

    private final String code;
    private final String describe;
}
