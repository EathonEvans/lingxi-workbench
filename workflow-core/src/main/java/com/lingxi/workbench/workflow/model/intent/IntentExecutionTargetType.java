package com.lingxi.workbench.workflow.model.intent;

/**
 * Application 中叶子意图允许绑定的执行目标类型。
 *
 * <p>发布时根据类型执行不同逻辑：</p>
 * <ul>
 *   <li><b>WORKFLOW</b> — 目标为独立 Workflow 的已发布版本（WorkflowVersion）。
 *       发布 Application 时解析并锁定 targetId 和 targetVersionId。</li>
 *   <li><b>AGENT</b> — 目标为独立 Agent 的已发布版本（AgentVersion）。
 *       包含 REACT 和 SUPERVISOR 类型。
 *       发布时解析 Agent 当前生效版本并锁定。</li>
 *   <li><b>FIXED_RESPONSE</b> — 目标为预定义的固定文本。不调用任何模型，
 *       不执行 Tool，不消耗 Token。适用于闲聊兜底、能力说明等场景。</li>
 * </ul>
 */
public enum IntentExecutionTargetType {

    /** 执行指定 Workflow 的已发布版本 */
    WORKFLOW("WORKFLOW", "Workflow"),

    /** 执行指定 Agent 的已发布版本 */
    AGENT("AGENT", "Agent"),

    /** 直接返回固定文本，不调用模型 */
    FIXED_RESPONSE("FIXED_RESPONSE", "固定答复");

    private final String code;
    private final String describe;

    IntentExecutionTargetType(String code, String describe) {
        this.code = code;
        this.describe = describe;
    }

    public String getCode() {
        return code;
    }

    public String getDescribe() {
        return describe;
    }
}
