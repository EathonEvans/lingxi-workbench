package com.lingxi.workbench.application.runtime.model;

import io.agentscope.core.state.Task;

import java.util.List;

/** AgentScope 任务清单的一次不可变快照。 */
public record ExecutionTaskSnapshotCommand(
        String runId,
        Integer revision,
        List<Task> tasks) {

    public ExecutionTaskSnapshotCommand {
        tasks = List.copyOf(tasks == null ? List.of() : tasks);
    }
}
