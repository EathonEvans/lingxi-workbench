package com.lingxi.workbench.agentruntime.checkpoint;

/** AgentScope 状态恢复之外仍由 Lingxi 持有的审批、调用意图和对账持久化边界。 */
public interface AgentScopeCheckpointStore {

    RuntimeInterruption interrupt(AgentScopeInterruptionRequest request);

    AgentScopeApprovedInvocation claimApproved(AgentScopeCheckpoint checkpoint, Long interactionId, String fingerprint);

    void complete(Long invocationId, String result);

    void fail(Long invocationId, RuntimeException exception);

    void markReconciliationPending(Long invocationId, RuntimeException exception);
}
