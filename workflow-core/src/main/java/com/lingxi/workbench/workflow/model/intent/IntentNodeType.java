package com.lingxi.workbench.workflow.model.intent;

/** 意图树节点类型。 */
public enum IntentNodeType {

    ROUTER("ROUTER", "分类节点"),
    LEAF("LEAF", "叶子意图");

    private final String code;
    private final String describe;

    IntentNodeType(String code, String describe) {
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
