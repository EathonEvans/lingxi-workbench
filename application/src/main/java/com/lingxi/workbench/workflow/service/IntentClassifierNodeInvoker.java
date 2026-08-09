package com.lingxi.workbench.workflow.service;

import com.lingxi.workbench.workflow.model.IntentClassifierNodeInvocation;

import java.util.Map;

/** Workflow 引擎调用发布意图树版本的端口。 */
@FunctionalInterface
public interface IntentClassifierNodeInvoker {

    /** 返回 status、intentCode、confidence 和 candidateCodes 标准输出。 */
    Map<String, Object> invoke(IntentClassifierNodeInvocation invocation);
}
