package com.lingxi.workbench.workflow.service;

import com.lingxi.workbench.workflow.model.IntentClassifierNodeInvocation;
import com.lingxi.workbench.workflow.model.IntentClassifierNodeResult;

/** Workflow 引擎调用发布意图树版本的端口。 */
@FunctionalInterface
public interface IntentClassifierNodeInvoker {

    /** 返回 V2 语义决策、候选证据和模型用量。 */
    IntentClassifierNodeResult invoke(IntentClassifierNodeInvocation invocation);
}
