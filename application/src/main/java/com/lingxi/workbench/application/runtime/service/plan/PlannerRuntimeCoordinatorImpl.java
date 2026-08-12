package com.lingxi.workbench.application.runtime.service.plan;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionDecisionAction;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.ExecutionDecision;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionRunStartCommand;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.PlanExecutionRequest;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlannerClarification;
import com.lingxi.workbench.application.runtime.model.PlannerRequest;
import com.lingxi.workbench.application.runtime.model.PlannerResult;
import com.lingxi.workbench.application.runtime.model.PlannerRuntimeRequest;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionFailureClassifier;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.plan.PlanExecutionService;
import com.lingxi.workbench.application.runtime.service.plan.PlannerRuntimeCoordinator;
import com.lingxi.workbench.application.runtime.service.plan.PlannerService;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CancellationException;

/** Planner运行协调实现。 */
@Service
@RequiredArgsConstructor
public class PlannerRuntimeCoordinatorImpl implements PlannerRuntimeCoordinator {

    private final ExecutionPersistenceService persistenceService;
    private final PlannerService plannerService;
    private final PlanExecutionService planExecutionService;
    private final ExecutionFailureClassifier failureClassifier;

    @Override
    public PlanExecutionResult execute(PlannerRuntimeRequest request) {
        persistenceService.startRun(ExecutionRunStartCommand.builder()
                .application(request.getConversationContext().getApplicationSnapshot())
                .turn(request.getExecutionContext().getTurn())
                .userId(request.getConversationContext().getLoginUser().id())
                .runId(request.getRunId())
                .orchestratorAgent(request.getPlanExecutor().getAgent())
                .build());
        publishPlanning(request.getRunId(), request.getExecutionContext());
        if (request.getExecutionContext().shouldFinalize()) {
            return planExecutionService.execute(planRequest(request, budgetBoundaryClarification()));
        }
        PlannerResult plannerResult;
        try {
            plannerResult = plannerService.decide(PlannerRequest.builder()
                    .conversationContext(request.getConversationContext())
                    .executionContext(request.getExecutionContext())
                    .planNo(1)
                    .confirmedCapabilityCodes(request.confirmedCapabilityCodes())
                    .planExecutor(request.getPlanExecutor())
                    .build());
            request.getExecutionContext().recordUsage(plannerResult.tokenUsage());
        } catch (CancellationException exception) {
            publishInitialDecisionFailure(request, ExecutionEventType.RUN_CANCELLED, "规划已取消", exception);
            throw exception;
        } catch (RuntimeException exception) {
            publishInitialDecisionFailure(request, ExecutionEventType.RUN_FAILED,
                    failureClassifier.classify(exception).clientMessage(), exception);
            throw exception;
        }
        return planExecutionService.execute(planRequest(request, plannerResult.decision()));
    }

    private static PlanExecutionRequest planRequest(PlannerRuntimeRequest request, ExecutionDecision decision) {
        return PlanExecutionRequest.builder()
                .runId(request.getRunId())
                .initialDecision(decision)
                .conversationContext(request.getConversationContext())
                .executionContext(request.getExecutionContext())
                .confirmedCapabilityCodes(request.confirmedCapabilityCodes())
                .planExecutor(request.getPlanExecutor())
                .build();
    }

    /** 上下文准备已耗尽工作预算时不再调用Planner，直接建立可恢复的澄清会话。 */
    private static ExecutionDecision budgetBoundaryClarification() {
        return ExecutionDecision.builder()
                .action(ExecutionDecisionAction.CLARIFY)
                .clarification(PlannerClarification.builder()
                        .question("本轮上下文处理已达到预算边界，请缩小问题范围或开启新对话后继续。")
                        .options(List.of())
                        .interactionType(InteractionSessionType.PLANNER_CLARIFICATION)
                        .capabilityCodes(List.of())
                        .build())
                .build();
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
