package com.lingxi.workbench.agentruntime.checkpoint;

/** Runtime Checkpoint 的跨模块持久化边界，由 Application 使用 PostgreSQL 实现。 */
public interface RuntimeCheckpointStore {

    RuntimeInterruption interrupt(HumanApprovalRequest request);

    Long claimApproved(RuntimeCheckpoint checkpoint, Long interactionId, String fingerprint);

    void complete(Long invocationId, String result);

    void fail(Long invocationId, RuntimeException exception);

    void markReconciliationPending(Long invocationId, RuntimeException exception);
}
