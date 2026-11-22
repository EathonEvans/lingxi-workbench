package com.lingxi.workbench.agentruntime.plan;

import io.agentscope.core.state.Task;

import java.util.List;

/** Application 对 AgentScope 任务快照和能力调用尝试的持久化边界。 */
public interface RuntimeTaskStore {

    void saveTasks(String runId, int revision, List<Task> tasks);

    void saveInvocation(AgentInvocationState invocation);
}
