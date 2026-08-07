package com.lingxi.workbench.application.runtime.service;

import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlannerContextResolution;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;

/**
 * Planner运行创建、初始决策和计划执行的协调边界。
 */
public interface PlannerRuntimeCoordinator {

    PlannerContextResolution resolveInteraction(ConversationContext context, ClarificationResponseRequest response);

    String startRun(ConversationContext context);

    PlanExecutionResult execute(PlannerRuntimeRequest request);
}
