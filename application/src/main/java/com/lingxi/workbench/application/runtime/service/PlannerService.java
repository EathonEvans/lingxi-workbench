package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.PlannerRequest;
import com.lingxi.workbench.application.runtime.model.PlannerResult;

/**
 * 将共享上下文转换为受发布能力目录约束的执行决策。
 */
public interface PlannerService {

    PlannerResult decide(PlannerRequest request);
}
