package com.lingxi.workbench.application.runtime.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionDecisionAction;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.ExecutionDecision;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionFailure;
import com.lingxi.workbench.application.runtime.model.ExecutionPlanSnapshotCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionStepAttemptCommand;
import com.lingxi.workbench.application.runtime.model.FinalSynthesisRequest;
import com.lingxi.workbench.application.runtime.model.FinalSynthesisResult;
import com.lingxi.workbench.application.runtime.model.InteractionSessionContext;
import com.lingxi.workbench.application.runtime.model.InteractionSessionOpenCommand;
import com.lingxi.workbench.application.runtime.model.PlanExecutionRequest;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlanStep;
import com.lingxi.workbench.application.runtime.model.PlanStepResult;
import com.lingxi.workbench.application.runtime.model.PlannerRequest;
import com.lingxi.workbench.application.runtime.model.PlannerResult;
import com.lingxi.workbench.application.runtime.model.PlannerClarification;
import com.lingxi.workbench.application.runtime.service.CapabilityExecutionService;
import com.lingxi.workbench.application.runtime.service.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.ExecutionFailureClassifier;
import com.lingxi.workbench.application.runtime.service.FinalSynthesisService;
import com.lingxi.workbench.application.runtime.service.InteractionSessionService;
import com.lingxi.workbench.application.runtime.service.PlanExecutionService;
import com.lingxi.workbench.application.runtime.service.PlannerService;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.bo.ChatPlanStepBO;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.workflow.model.PlannerPolicy;
import com.lingxi.workbench.workflow.model.WorkflowNodeTrace;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

/** 串行步骤执行、有限重规划和无Tool最终综合实现。 */
@Service
@RequiredArgsConstructor
public class PlanExecutionServiceImpl implements PlanExecutionService {

    private final CapabilityExecutionService capabilityExecutionService;
    private final PlannerService plannerService;
    private final FinalSynthesisService finalSynthesisService;
    private final ExecutionPersistenceService persistenceService;
    private final InteractionSessionService interactionSessionService;
    private final PlanInputCompiler inputCompiler;
    private final ExecutionFailureClassifier failureClassifier;
    private final ObservationRegistry observationRegistry;

    @Override
    public PlanExecutionResult execute(PlanExecutionRequest request) {
        requireRequest(request);
        Observation observation = Observation.createNotStarted("lingxi.ai.plan", observationRegistry)
                .contextualName("plan-and-execute")
                .highCardinalityKeyValue("lingxi.run.id", request.getRunId());
        return observation.observe(() -> executeObserved(request));
    }

    private PlanExecutionResult executeObserved(PlanExecutionRequest request) {
        try {
            return executePlan(request);
        } catch (CancellationException exception) {
            publishTerminalFailure(request, ExecutionEventType.RUN_CANCELLED, "计划执行已取消", exception);
            throw exception;
        } catch (RuntimeException exception) {
            ExecutionFailure failure = failureClassifier.classify(exception);
            publishTerminalFailure(request, ExecutionEventType.RUN_FAILED, failure.clientMessage(), exception);
            throw exception;
        }
    }

    private PlanExecutionResult executePlan(PlanExecutionRequest request) {
        PlannerPolicy policy = request.getConversationContext().getApplicationSnapshot().definition().plannerPolicy();
        Map<String, ApplicationCapabilityDefinition> capabilities = capabilityIndex(request);
        ExecutionDecision decision = request.getInitialDecision();
        int planNo = 1;
        int replans = 0;
        Map<String, Integer> capabilityCalls = new HashMap<>();
        Set<String> completedWriteCapabilities = new HashSet<>();
        List<PlanStepResult> completedResults = new ArrayList<>();
        List<WorkflowNodeTrace> traces = new ArrayList<>();
        Long modelInstanceId = null;

        while (true) {
            ensureActive(request);
            persistenceService.appendPlan(new ExecutionPlanSnapshotCommand(request.getRunId(), planNo, decision));
            if (decision.getAction() == ExecutionDecisionAction.CLARIFY) {
                return clarify(request, decision, planNo);
            }
            if (!request.getExecutionContext().hasFinalizationCapacity()) {
                return clarifyAtBoundary(request, planNo,
                        "Application运行预算已到硬边界，未继续发起新的能力调用");
            }
            if (decision.getAction() != ExecutionDecisionAction.EXECUTE_PLAN) {
                CapabilityExecutionResult result = executeDirect(request, decision, planNo, capabilities);
                publish(request, event(request, ExecutionEventType.RUN_COMPLETED).planNo(planNo).build(), List.of());
                return new PlanExecutionResult(result.content(), result.modelInstanceId(), result.traces());
            }
            publish(request, event(request, ExecutionEventType.PLAN_CREATED)
                    .planNo(planNo)
                    .displayName(decision.getPlan().getTitle())
                    .build(), planSteps(decision));
            if (completedResults.isEmpty() && request.getExecutionContext().hasReachedTokenSoftLimit()) {
                return clarifyAtBoundary(request, planNo,
                        "Planner已用尽普通工作Token，宽限额度仅保留给最终回答");
            }
            try {
                for (PlanStep step : decision.getPlan().steps()) {
                    ensureActive(request);
                    if (!completedResults.isEmpty() && request.getExecutionContext().shouldFinalize()) {
                        return finalizePlan(request, completedResults, modelInstanceId, traces, planNo,
                                "Application运行预算已到软边界，未继续执行剩余步骤");
                    }
                    ApplicationCapabilityDefinition capability = requireCapability(step.getCapabilityCode(), capabilities);
                    int nextCalls = capabilityCalls.getOrDefault(capability.getCode(), 0) + 1;
                    if (nextCalls > capability.getMaxCalls()) {
                        throw new StepFailure(step, "CAPABILITY_CALL_LIMIT");
                    }
                    if (capability.getRiskLevel() != CapabilityRiskLevel.READ_ONLY
                            && completedWriteCapabilities.contains(capability.getCode())) {
                        throw new StepFailure(step, "SIDE_EFFECT_ALREADY_COMPLETED");
                    }
                    capabilityCalls.put(capability.getCode(), nextCalls);
                    CapabilityExecutionResult result = executeStep(
                            request, planNo, step, capability, completedResults, true);
                    completedResults.add(PlanStepResult.builder()
                            .stepId(step.getId())
                            .name(step.getName())
                            .capabilityCode(step.getCapabilityCode())
                            .content(result.content())
                            .build());
                    traces.addAll(result.traces());
                    if (result.modelInstanceId() != null) {
                        modelInstanceId = result.modelInstanceId();
                    }
                    if (capability.getRiskLevel() != CapabilityRiskLevel.READ_ONLY) {
                        completedWriteCapabilities.add(capability.getCode());
                    }
                }
                return finalizePlan(request, completedResults, modelInstanceId, traces, planNo, null);
            } catch (StepFailure failure) {
                if (!failure.failure.retryable()) {
                    throw invalid("计划步骤执行失败：" + failure.failure.clientMessage());
                }
                boolean replanUnavailable = replans >= policy.getMaxReplans()
                        || request.getExecutionContext().shouldFinalize();
                if (replanUnavailable) {
                    String limitation = "计划步骤未完成，且无法继续重规划：" + failure.failure.clientMessage();
                    if (!completedResults.isEmpty()) {
                        return finalizePlan(request, completedResults, modelInstanceId, traces, planNo, limitation);
                    }
                    return clarifyAtBoundary(request, planNo, limitation);
                }
                replans++;
                planNo++;
                publish(request, event(request, ExecutionEventType.REPLANNING)
                        .planNo(planNo).stepId(failure.step.getId())
                        .message(failure.failure.clientMessage() + "，正在重新规划").build(), List.of());
                PlannerResult replanned = plannerService.decide(PlannerRequest.builder()
                        .conversationContext(request.getConversationContext())
                        .executionContext(request.getExecutionContext())
                        .planNo(planNo)
                        .failureObservation(failure.observation())
                        .confirmedCapabilityCodes(request.confirmedCapabilityCodes())
                        .build());
                request.getExecutionContext().recordUsage(replanned.tokenUsage());
                decision = replanned.decision();
            }
        }
    }

    /**
     * 计划正常完成或触及软边界后统一进入无Tool最终综合。
     */
    private PlanExecutionResult finalizePlan(PlanExecutionRequest request, List<PlanStepResult> completedResults,
                                             Long modelInstanceId, List<WorkflowNodeTrace> traces, int planNo,
                                             String limitation) {
        boolean limited = Objects.nonNull(limitation) && !limitation.isBlank();
        String progressMessage = limited ? "已停止继续探索，正在基于现有结果整理回答" : "正在整理最终回答";
        publish(request, event(request, ExecutionEventType.FINALIZING)
                .planNo(planNo).message(progressMessage).build(), List.of());
        if (!request.getExecutionContext().hasFinalizationCapacity()) {
            PlanStepResult lastResult = completedResults.getLast();
            publish(request, event(request, ExecutionEventType.RUN_COMPLETED)
                    .planNo(planNo).message("运行预算已到硬边界，已返回最后一个完整步骤结果").build(), List.of());
            return new PlanExecutionResult(lastResult.getContent(), modelInstanceId, traces);
        }
        FinalSynthesisResult synthesis = finalSynthesisService.synthesize(new FinalSynthesisRequest(
                request.getConversationContext(), request.getExecutionContext(), completedResults, limitation));
        request.getExecutionContext().recordUsage(synthesis.tokenUsage());
        String completedMessage = limited ? "已基于现有信息完成回答" : null;
        publish(request, event(request, ExecutionEventType.RUN_COMPLETED)
                .planNo(planNo).message(completedMessage).build(), List.of());
        return new PlanExecutionResult(synthesis.content(), modelInstanceId, traces);
    }

    /** 没有任何完整步骤结果时不伪造答案，改为建立可恢复的用户澄清会话。 */
    private PlanExecutionResult clarifyAtBoundary(PlanExecutionRequest request, int planNo, String limitation) {
        String question = "本轮执行已达到处理边界，现有信息不足以形成可靠答案。请补充最关键的目标、材料或限制条件后继续。";
        ExecutionDecision clarification = ExecutionDecision.builder()
                .action(ExecutionDecisionAction.CLARIFY)
                .clarification(PlannerClarification.builder()
                        .question(question)
                        .options(List.of())
                        .interactionType(InteractionSessionType.PLANNER_CLARIFICATION)
                        .capabilityCodes(List.of())
                        .build())
                .build();
        publish(request, event(request, ExecutionEventType.FINALIZING)
                .planNo(planNo).message(limitation).build(), List.of());
        return clarify(request, clarification, planNo);
    }

    private void publishTerminalFailure(PlanExecutionRequest request, ExecutionEventType eventType, String message, RuntimeException original) {
        try {
            publish(request, event(request, eventType).message(message).build(), List.of());
        } catch (RuntimeException persistenceFailure) {
            original.addSuppressed(persistenceFailure);
        }
    }

    private PlanExecutionResult clarify(PlanExecutionRequest request, ExecutionDecision decision, int planNo) {
        boolean riskConfirmation = decision.getClarification().getInteractionType()
                == InteractionSessionType.RISK_CONFIRMATION;
        PlannerClarification interactionClarification = PlannerClarification.builder()
                .question(decision.getClarification().getQuestion())
                .options(riskConfirmation ? List.of("确认执行", "取消") : decision.getClarification().options())
                .interactionType(decision.getClarification().getInteractionType())
                .capabilityCodes(decision.getClarification().capabilityCodes())
                .build();
        InteractionSessionContext sessionContext = InteractionSessionContext.builder()
                .clarification(interactionClarification)
                .capabilityCodes(decision.getClarification().capabilityCodes())
                .originalQuery(request.getConversationContext().getOriginalQuery())
                .build();
        request.getExecutionContext().getEventPublisher().publishClarification(
                interactionSessionService.open(InteractionSessionOpenCommand.builder()
                        .application(request.getConversationContext().getApplicationSnapshot())
                        .turn(request.getExecutionContext().getTurn())
                        .userId(request.getConversationContext().getLoginUser().id())
                        .runId(request.getRunId())
                        .interactionType(decision.getClarification().getInteractionType())
                        .context(sessionContext)
                        .build()));
        publish(request, event(request, ExecutionEventType.CLARIFICATION_REQUIRED)
                .planNo(planNo).message(decision.getClarification().getQuestion()).build(), List.of());
        return new PlanExecutionResult(decision.getClarification().getQuestion(), null, List.of());
    }

    private CapabilityExecutionResult executeDirect(PlanExecutionRequest request, ExecutionDecision decision, int planNo,
                                                    Map<String, ApplicationCapabilityDefinition> capabilities) {
        ApplicationCapabilityDefinition capability = requireCapability(decision.getCapabilityCode(), capabilities);
        PlanStep step = PlanStep.builder()
                .id("direct")
                .name(capability.getName())
                .capabilityCode(capability.getCode())
                .dependsOn(List.of())
                .inputs(List.of())
                .build();
        return executeStep(request, planNo, step, capability, List.of(), false);
    }

    private CapabilityExecutionResult executeStep(PlanExecutionRequest request, int planNo, PlanStep step,
                                                  ApplicationCapabilityDefinition capability,
                                                  List<PlanStepResult> completedResults, boolean publishStepOutput) {
        Observation observation = Observation.createNotStarted("lingxi.ai.plan.step", observationRegistry)
                .contextualName("plan-step")
                .lowCardinalityKeyValue("lingxi.capability.type", capability.getType().name())
                .highCardinalityKeyValue("lingxi.run.id", request.getRunId())
                .highCardinalityKeyValue("lingxi.plan.no", String.valueOf(planNo))
                .highCardinalityKeyValue("lingxi.step.id", step.getId())
                .highCardinalityKeyValue("lingxi.capability.code", capability.getCode());
        return observation.observe(() -> executeObservedStep(
                request, planNo, step, capability, completedResults, publishStepOutput));
    }

    private CapabilityExecutionResult executeObservedStep(PlanExecutionRequest request, int planNo, PlanStep step,
                                                           ApplicationCapabilityDefinition capability,
                                                           List<PlanStepResult> completedResults,
                                                           boolean publishStepOutput) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        publish(request, event(request, ExecutionEventType.STEP_STARTED)
                .planNo(planNo).stepId(step.getId()).displayName(step.getName())
                .stepStatus(ExecutionStepStatus.RUNNING).build(), List.of());
        try {
            JSONObject inputs = inputCompiler.compile(step, capability,
                    request.getConversationContext().routingQuery(), completedResults);
            ChatExecutionContext stepContext = resolveStepContext(
                    request, planNo, step, capability, inputs, publishStepOutput);
            CapabilityExecutionResult result = capabilityExecutionService.execute(
                    new CapabilityExecutionRequest(capability, inputs, stepContext));
            persistenceService.appendStepAttempt(attempt(request, planNo, step,
                    new StepAttemptOutcome(ExecutionStepStatus.COMPLETED, result.content(), null), startedAt));
            publish(request, event(request, ExecutionEventType.STEP_COMPLETED)
                    .planNo(planNo).stepId(step.getId()).displayName(step.getName())
                    .stepStatus(ExecutionStepStatus.COMPLETED).build(), List.of());
            return result;
        } catch (CancellationException exception) {
            persistenceService.appendStepAttempt(attempt(request, planNo, step,
                    new StepAttemptOutcome(ExecutionStepStatus.CANCELLED, null, "CANCELLED"), startedAt));
            throw exception;
        } catch (RuntimeException exception) {
            ExecutionFailure failure = failureClassifier.classify(exception);
            persistenceService.appendStepAttempt(attempt(request, planNo, step,
                    new StepAttemptOutcome(ExecutionStepStatus.FAILED, null, failure.code()), startedAt));
            publish(request, event(request, ExecutionEventType.STEP_FAILED)
                    .planNo(planNo).stepId(step.getId()).displayName(step.getName())
                    .stepStatus(ExecutionStepStatus.FAILED).message(failure.clientMessage()).build(), List.of());
            throw new StepFailure(step, failure, exception);
        }
    }

    /** Agent与Workflow步骤使用编译后的问题作用域，Tool继续读取结构化命名入参。 */
    private static ChatExecutionContext resolveStepContext(PlanExecutionRequest request, int planNo, PlanStep step,
                                                           ApplicationCapabilityDefinition capability,
                                                           JSONObject inputs, boolean publishStepOutput) {
        ChatExecutionContext context = request.getExecutionContext();
        if (capability.getType() == ApplicationCapabilityType.TOOL) {
            return context;
        }
        String query = inputs.getString("query");
        if (query == null || query.isBlank()) {
            throw invalid("计划步骤执行问题不能为空");
        }
        if (publishStepOutput) {
            return context.forkForPlanStep(query, request.getRunId(), planNo, step.getId());
        }
        return context.forkForQuery(query);
    }

    private static ExecutionStepAttemptCommand attempt(PlanExecutionRequest request, int planNo, PlanStep step,
                                                       StepAttemptOutcome outcome, OffsetDateTime startedAt) {
        return ExecutionStepAttemptCommand.builder()
                .runId(request.getRunId())
                .planNo(planNo)
                .attemptNo(1)
                .step(step)
                .status(outcome.status())
                .resultContent(outcome.result())
                .errorCode(outcome.errorCode())
                .startedAt(startedAt)
                .build();
    }

    private void publish(PlanExecutionRequest request, ChatExecutionTimelineEventBO timeline,
                         List<ChatPlanStepBO> steps) {
        persistenceService.appendEvent(ExecutionEventCommand.builder()
                .runId(request.getRunId())
                .eventType(timeline.getEventType())
                .planNo(timeline.getPlanNo())
                .stepId(timeline.getStepId())
                .displayName(timeline.getDisplayName())
                .stepStatus(timeline.getStepStatus())
                .message(timeline.getMessage())
                .build());
        ChatExecutionTimelineEventBO published = ChatExecutionTimelineEventBO.builder()
                .runId(timeline.getRunId())
                .eventType(timeline.getEventType())
                .planNo(timeline.getPlanNo())
                .stepId(timeline.getStepId())
                .displayName(timeline.getDisplayName())
                .stepStatus(timeline.getStepStatus())
                .message(timeline.getMessage())
                .steps(steps)
                .build();
        request.getExecutionContext().getEventPublisher().publishExecution(published);
    }

    private static ChatExecutionTimelineEventBO.ChatExecutionTimelineEventBOBuilder event(
            PlanExecutionRequest request, ExecutionEventType type) {
        return ChatExecutionTimelineEventBO.builder().runId(request.getRunId()).eventType(type);
    }

    private static List<ChatPlanStepBO> planSteps(ExecutionDecision decision) {
        return decision.getPlan().steps().stream()
                .map(step -> new ChatPlanStepBO(step.getId(), step.getName(), step.getCapabilityCode()))
                .toList();
    }

    private static Map<String, ApplicationCapabilityDefinition> capabilityIndex(PlanExecutionRequest request) {
        return request.getConversationContext().getApplicationSnapshot().definition().capabilityCatalog()
                .capabilities().stream()
                .collect(Collectors.toMap(ApplicationCapabilityDefinition::getCode, capability -> capability));
    }

    private static ApplicationCapabilityDefinition requireCapability(
            String code, Map<String, ApplicationCapabilityDefinition> capabilities) {
        ApplicationCapabilityDefinition capability = capabilities.get(code);
        if (capability == null) {
            throw invalid("执行决策引用了未发布能力：" + code);
        }
        return capability;
    }

    private static void ensureActive(PlanExecutionRequest request) {
        if (request.getExecutionContext().isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Plan-and-Execute已取消");
        }
        request.getExecutionContext().ensureWithinDeadline();
    }

    private static void requireRequest(PlanExecutionRequest request) {
        if (request == null || request.getRunId() == null || request.getRunId().isBlank()
                || request.getInitialDecision() == null || request.getConversationContext() == null
                || request.getExecutionContext() == null) {
            throw invalid("计划执行请求不完整");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatusEnum.CONFLICT, message);
    }

    /**
     * @param status    步骤终态
     * @param result    成功结果
     * @param errorCode 失败编码
     */
    private record StepAttemptOutcome(ExecutionStepStatus status, String result, String errorCode) {
    }

    private static final class StepFailure extends RuntimeException {
        private final PlanStep step;
        private final ExecutionFailure failure;

        private StepFailure(PlanStep step, String errorCode) {
            this(step, new ExecutionFailure(errorCode, "计划步骤执行失败", false), null);
        }

        private StepFailure(PlanStep step, ExecutionFailure failure, Throwable cause) {
            super(failure.code(), cause);
            this.step = Objects.requireNonNull(step);
            this.failure = Objects.requireNonNull(failure);
        }

        private String observation() {
            return "stepId=" + step.getId() + ", capabilityCode=" + step.getCapabilityCode()
                    + ", errorCode=" + failure.code() + ", reason=" + failure.clientMessage();
        }
    }
}
