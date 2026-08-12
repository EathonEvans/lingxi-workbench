package com.lingxi.workbench.application.runtime.service.plan;

import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;

/**
 * Planner运行创建、初始决策和计划执行的协调边界。
 */
public interface PlannerRuntimeCoordinator {

    PlanExecutionResult execute(PlannerRuntimeRequest request);
}
