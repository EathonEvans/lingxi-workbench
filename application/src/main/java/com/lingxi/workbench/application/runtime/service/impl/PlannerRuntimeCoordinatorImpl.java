package com.lingxi.workbench.application.runtime.service.impl;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionRunStartCommand;
import com.lingxi.workbench.application.runtime.model.PlanExecutionRequest;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlannerContextResolution;
import com.lingxi.workbench.application.runtime.model.PlannerInteractionResolution;
import com.lingxi.workbench.application.runtime.model.PlannerRequest;
import com.lingxi.workbench.application.runtime.model.PlannerResult;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;
import com.lingxi.workbench.application.runtime.service.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.InteractionSessionService;
import com.lingxi.workbench.application.runtime.service.PlanExecutionService;
import com.lingxi.workbench.application.runtime.service.PlannerRuntimeCoordinator;
import com.lingxi.workbench.application.runtime.service.PlannerService;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.PlannerPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CancellationException;
import java.util.Optional;
import java.util.Set;

/** Planner运行协调实现。 */
@Service
@RequiredArgsConstructor
public class PlannerRuntimeCoordinatorImpl implements PlannerRuntimeCoordinator {

    private final InteractionSessionService interactionSessionService;
    private final ExecutionPersistenceService persistenceService;
    private final PlannerService plannerService;
    private final PlanExecutionService planExecutionService;

    @Override
    public PlannerContextResolution resolveInteraction(ConversationContext context,
                                                       ClarificationResponseRequest response) {
        Optional<PlannerInteractionResolution> resolved = interactionSessionService.resolve(
                context.getApplicationSnapshot(), context.getTurn(), context.getLoginUser().id(),
                context.getOriginalQuery(), response);
        if (resolved.isEmpty()) {
            return new PlannerContextResolution(context, false, Set.of());
        }
        if (!context.getApplicationSnapshot().definition().plannerPolicy().isEnabled()) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "当前Application版本未启用Planner");
        }
        PlannerInteractionResolution interaction = resolved.get();
        String resumedQuery = interaction.originalQuery() + "\n补充信息：" + interaction.response();
        return new PlannerContextResolution(
                context.withRoutingQuery(resumedQuery), true, interaction.confirmedCapabilityCodes());
    }

    @Override
    public String startRun(ConversationContext context) {
        return persistenceService.startRun(ExecutionRunStartCommand.builder()
                .application(context.getApplicationSnapshot())
                .turn(context.getTurn())
                .userId(context.getLoginUser().id())
                .plannerPolicy(context.getApplicationSnapshot().definition().plannerPolicy())
                .build());
    }

    @Override
    public PlanExecutionResult execute(PlannerRuntimeRequest request) {
        publishPlanning(request.getRunId(), request.getExecutionContext());
        PlannerResult plannerResult;
        try {
            PlannerPolicy policy = request.getConversationContext().getApplicationSnapshot().definition().plannerPolicy();
            plannerResult = plannerService.decide(PlannerRequest.builder()
                    .conversationContext(request.getConversationContext())
                    .planNo(1)
                    .confirmedCapabilityCodes(request.confirmedCapabilityCodes())
                    .availableModelCalls(policy.getMaxModelCalls())
                    .build());
        } catch (CancellationException exception) {
            publishInitialDecisionFailure(request, ExecutionEventType.RUN_CANCELLED, "规划已取消", exception);
            throw exception;
        } catch (RuntimeException exception) {
            publishInitialDecisionFailure(request, ExecutionEventType.RUN_FAILED, "规划失败", exception);
            throw exception;
        }
        request.getExecutionContext().recordUsage(plannerResult.tokenUsage());
        return planExecutionService.execute(PlanExecutionRequest.builder()
                .runId(request.getRunId())
                .initialDecision(plannerResult.decision())
                .conversationContext(request.getConversationContext())
                .executionContext(request.getExecutionContext())
                .confirmedCapabilityCodes(request.confirmedCapabilityCodes())
                .startedAt(request.getStartedAt())
                .initialPlannerModelCalls(plannerResult.modelCalls())
                .build());
    }

    private void publishInitialDecisionFailure(PlannerRuntimeRequest request, ExecutionEventType type,
                                               String message, RuntimeException original) {
        try {
            persistenceService.appendEvent(ExecutionEventCommand.builder()
                    .runId(request.getRunId())
                    .eventType(type)
                    .message(message)
                    .build());
            request.getExecutionContext().getEventPublisher().publishExecution(ChatExecutionTimelineEventBO.builder()
                    .runId(request.getRunId())
                    .eventType(type)
                    .message(message)
                    .build());
        } catch (RuntimeException persistenceFailure) {
            original.addSuppressed(persistenceFailure);
        }
    }

    private void publishPlanning(String runId, ChatExecutionContext executionContext) {
        persistenceService.appendEvent(ExecutionEventCommand.builder()
                .runId(runId)
                .eventType(ExecutionEventType.PLANNING)
                .message("正在规划任务")
                .build());
        executionContext.getEventPublisher().publishExecution(ChatExecutionTimelineEventBO.builder()
                .runId(runId)
                .eventType(ExecutionEventType.PLANNING)
                .message("正在规划任务")
                .build());
    }
}
