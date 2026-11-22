package com.lingxi.workbench.workflow.model.intent;

/**
 * 意图树节点类型。
 *
 * <ul>
 *   <li><b>ROUTER</b> — 分类节点，仅用于组织意图层级结构。
 *       不参与向量召回候选，不绑定执行目标。
 *       只能作为非叶子节点存在，其子节点可以是 ROUTER 或 LEAF。</li>
 *   <li><b>LEAF</b> — 叶子意图节点，是实际可路由的用户意图。
 *       参与向量化（原型 + 正例），是运行时候选召回的唯一目标。
 *       Application 通过 {@code ApplicationIntentMapping} 为其绑定执行目标。</li>
 * </ul>
 */
public enum IntentNodeType {

    /** 分类节点，仅用于组织层级，不参与路由 */
    ROUTER("ROUTER", "分类节点"),

    /** 叶子意图节点，可路由到具体执行目标 */
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