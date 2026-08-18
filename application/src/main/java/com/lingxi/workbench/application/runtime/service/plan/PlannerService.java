package com.lingxi.workbench.application.runtime.service.plan;

import com.lingxi.workbench.agentruntime.planner.PlannerRunResult;
import com.lingxi.workbench.application.runtime.model.PlannerRequest;

/**
 * 将共享上下文转换为受发布能力目录约束的执行决策。
 */
public interface PlannerService {

    PlannerRunResult decide(PlannerRequest request);
}
