package com.lingxi.workbench.agentruntime.plan;

/** Runtime 与锁定 Agent、Workflow、平台 Tool 的唯一调用边界。 */
@FunctionalInterface
public interface RuntimeCapabilityInvoker {

    RuntimeCapabilityResult invoke(RuntimeCapabilityInvocation invocation);
}
