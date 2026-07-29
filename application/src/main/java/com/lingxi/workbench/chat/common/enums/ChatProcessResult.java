package com.lingxi.workbench.chat.common.enums;

/**
 * 对话处理节点执行结果。
 */
public enum ChatProcessResult {

    /**
     * 继续执行后继节点。
     */
    CONTINUE,

    /**
     * 当前节点已形成最终决策，不再执行后继节点。
     */
    STOP
}
