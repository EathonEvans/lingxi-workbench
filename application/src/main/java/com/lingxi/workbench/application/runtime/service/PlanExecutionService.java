package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.PlanExecutionRequest;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;

/**
 * 有限串行计划、重规划和最终综合的执行边界。
 */
public interface PlanExecutionService {

    PlanExecutionResult execute(PlanExecutionRequest request);
}
