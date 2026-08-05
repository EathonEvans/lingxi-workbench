package com.lingxi.workbench.workflow.model.intent;

/** Application 中叶子意图允许绑定的执行目标类型。 */
public enum IntentExecutionTargetType {

    WORKFLOW("WORKFLOW", "Application Workflow"),
    AGENT("AGENT", "Agent"),
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
