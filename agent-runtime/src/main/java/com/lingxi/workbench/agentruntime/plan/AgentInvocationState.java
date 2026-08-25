package com.lingxi.workbench.agentruntime.plan;

import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** 每个 AgentScope 任务委派独享的调用状态，不与其他子 Agent 共享消息或局部结果。 */
@Getter
public final class AgentInvocationState {

    private final String runId;
    private final int taskRevision;
    private final int attemptNo;
    private final RuntimeTaskStep step;
    private final RuntimeCapability capability;
    private final Map<String, String> inputs;
    private final String inputFingerprint;
    private final Instant startedAt;
    private AgentInvocationStatus status = AgentInvocationStatus.RUNNING;
    private String result;
    private String errorCode;

    public AgentInvocationState(String runId, int taskRevision, int attemptNo, RuntimeTaskStep step,
                                RuntimeCapability capability, Map<String, String> inputs,
                                String inputFingerprint) {
        this.runId = Objects.requireNonNull(runId, "runId不能为空");
        this.taskRevision = taskRevision;
        this.attemptNo = attemptNo;
        this.step = Objects.requireNonNull(step, "任务步骤不能为空");
        this.capability = Objects.requireNonNull(capability, "运行能力不能为空");
        this.inputs = Map.copyOf(Objects.requireNonNull(inputs, "能力输入不能为空"));
        this.inputFingerprint = Objects.requireNonNull(inputFingerprint, "能力输入指纹不能为空");
        this.startedAt = Instant.now();
    }

    public void complete(String content) {
        finish(AgentInvocationStatus.COMPLETED, content, null);
    }

    public void reuse(String content) {
        finish(AgentInvocationStatus.REUSED, content, null);
    }

    public void fail(String code) {
        finish(AgentInvocationStatus.FAILED, null, code);
    }

    public void cancel() {
        finish(AgentInvocationStatus.CANCELLED, null, "CANCELLED");
    }

    public void interrupt() {
        finish(AgentInvocationStatus.INTERRUPTED, null, "HUMAN_REQUIRED");
    }

    private void finish(AgentInvocationStatus target, String content, String code) {
        if (status != AgentInvocationStatus.RUNNING) {
            throw new IllegalStateException("任务步骤已经进入终态");
        }
        status = target;
        result = content;
        errorCode = code;
    }
}
