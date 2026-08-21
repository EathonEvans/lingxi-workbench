package com.lingxi.workbench.agentruntime.plan;

import com.lingxi.workbench.agentruntime.planner.PlannerDecision;

/** Application 对 Runtime 计划快照和步骤尝试的持久化边界。 */
public interface RuntimePlanStore {

    void savePlan(String runId, int planNo, PlannerDecision decision);

    void saveInvocation(AgentInvocationState invocation);
}
