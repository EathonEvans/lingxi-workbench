package com.lingxi.workbench.infra.ai.tool;

import com.lingxi.workbench.infra.ai.tool.model.AgentToolReconciliationRequest;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolReconciliationResult;

/**
 * 具有外部副作用的 Tool 对已批准调用提供的结果查询契约。
 *
 * <p>查询必须使用同一个幂等键，不得再次触发原业务动作。若需要反向操作，
 * 实现只返回 {@code COMPENSATION_REQUIRED}，由独立人工审批流程决定后续处理。</p>
 */
@FunctionalInterface
public interface AgentToolReconciliation {

    /** 按稳定幂等键查询外部效果，不执行原 Tool 或补偿动作。 */
    AgentToolReconciliationResult query(AgentToolReconciliationRequest request);
}
