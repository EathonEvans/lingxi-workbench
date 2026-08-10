package com.lingxi.workbench.application.runtime.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionDecisionAction;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.common.enums.StepInputSource;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionRequest;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.ExecutionDecision;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
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
import com.lingxi.workbench.application.runtime.model.StepInputReference;
import com.lingxi.workbench.application.runtime.service.CapabilityExecutionService;
import com.lingxi.workbench.application.runtime.service.ExecutionPersistenceService;
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

import java.time.Duration;
import java.time.Instant;
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

    private static final String STEP_FAILED_CODE = "CAPABILITY_EXECUTION_FAILED";

    private final CapabilityExecutionService capabilityExecutionService;
    private final PlannerService plannerService;
    private final FinalSynthesisService finalSynthesisService;
    private final ExecutionPersistenceService persistenceService;
    private final InteractionSessionService interactionSessionService;
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
            publishTerminalFailure(request, ExecutionEventType.RUN_FAILED, "计划执行失败", exception);
            throw exception;
        }
    }

    private PlanExecutionResult executePlan(PlanExecutionRequest request) {
        PlannerPolicy policy = request.getConversationContext().getApplicationSnapshot().definition().plannerPolicy();
        Map<String, ApplicationCapabilityDefinition> capabilities = capabilityIndex(request);
        ExecutionDecision decision = request.getInitialDecision();
        int planNo = 1;
        int modelCalls = request.getInitialPlannerModelCalls();
        int toolCalls = 0;
        int replans = 0;
        Map<String, Integer> capabilityCalls = new HashMap<>();
        Set<String> completedWriteCapabilities = new HashSet<>();
        List<PlanStepResult> completedResults = new ArrayList<>();
        List<WorkflowNodeTrace> traces = new ArrayList<>();
        Long modelInstanceId = null;

        while (true) {
            ensureActive(request, policy);
            persistenceService.appendPlan(new ExecutionPlanSnapshotCommand(request.getRunId(), planNo, decision));
            if (decision.getAction() == ExecutionDecisionAction.CLARIFY) {
                return clarify(request, decision, planNo);
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
            try {
                for (PlanStep step : decision.getPlan().steps()) {
                    ensureActive(request, policy);
                    ApplicationCapabilityDefinition capability = requireCapability(step.getCapabilityCode(), capabilities);
                    int nextCalls = capabilityCalls.getOrDefault(capability.getCode(), 0) + 1;
                    if (nextCalls > capability.getMaxCalls()) {
                        throw new StepFailure(step, "CAPABILITY_CALL_LIMIT");
                    }
                    if (capability.getType() == ApplicationCapabilityType.TOOL && ++toolCalls > policy.getMaxToolCalls()) {
                        throw new StepFailure(step, "TOOL_CALL_LIMIT");
                    }
                    if (capability.getRiskLevel() != CapabilityRiskLevel.READ_ONLY
                            && completedWriteCapabilities.contains(capability.getCode())) {
                        throw new StepFailure(step, "SIDE_EFFECT_ALREADY_COMPLETED");
                    }
                    capabilityCalls.put(capability.getCode(), nextCalls);
                    CapabilityExecutionResult result = executeStep(
                            request, planNo, step, capability, completedResults);
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
                if (++modelCalls > policy.getMaxModelCalls()) {
                    throw invalid("Planner模型调用次数超过发布上限");
                }
                FinalSynthesisResult synthesis = finalSynthesisService.synthesize(
                        new FinalSynthesisRequest(request.getConversationContext(), completedResults));
                request.getExecutionContext().recordUsage(synthesis.tokenUsage());
                publish(request, event(request, ExecutionEventType.RUN_COMPLETED).planNo(planNo).build(), List.of());
                return new PlanExecutionResult(synthesis.content(), modelInstanceId, traces);
            } catch (StepFailure failure) {
                if (replans >= policy.getMaxReplans()) {
                    throw invalid("计划步骤执行失败且已达到重规划上限");
                }
                replans++;
                planNo++;
                int availableModelCalls = policy.getMaxModelCalls() - modelCalls;
                if (availableModelCalls < 1) {
                    throw invalid("Planner模型调用次数超过发布上限");
                }
                publish(request, event(request, ExecutionEventType.REPLANNING)
                        .planNo(planNo).stepId(failure.step.getId()).message("步骤失败，正在重新规划").build(), List.of());
                PlannerResult replanned = plannerService.decide(PlannerRequest.builder()
                        .conversationContext(request.getConversationContext())
                        .planNo(planNo)
                        .failureObservation(failure.observation())
                        .confirmedCapabilityCodes(request.confirmedCapabilityCodes())
                        .availableModelCalls(availableModelCalls)
                        .build());
                if (replanned.modelCalls() < 1 || replanned.modelCalls() > availableModelCalls) {
                    throw invalid("Planner返回的模型调用次数无效");
                }
                modelCalls += replanned.modelCalls();
                request.getExecutionContext().recordUsage(replanned.tokenUsage());
                decision = replanned.decision();
            }
        }
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
        return executeStep(request, planNo, step, capability, List.of());
    }

    private CapabilityExecutionResult executeStep(PlanExecutionRequest request, int planNo, PlanStep step,
                                                  ApplicationCapabilityDefinition capability,
                                                  List<PlanStepResult> completedResults) {
        Observation observation = Observation.createNotStarted("lingxi.ai.plan.step", observationRegistry)
                .contextualName("plan-step")
                .lowCardinalityKeyValue("lingxi.capability.type", capability.getType().name())
                .highCardinalityKeyValue("lingxi.run.id", request.getRunId())
                .highCardinalityKeyValue("lingxi.plan.no", String.valueOf(planNo))
                .highCardinalityKeyValue("lingxi.step.id", step.getId())
                .highCardinalityKeyValue("lingxi.capability.code", capability.getCode());
        return observation.observe(() -> executeObservedStep(request, planNo, step, capability, completedResults));
    }

    private CapabilityExecutionResult executeObservedStep(PlanExecutionRequest request, int planNo, PlanStep step,
                                                           ApplicationCapabilityDefinition capability,
                                                           List<PlanStepResult> completedResults) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        publish(request, event(request, ExecutionEventType.STEP_STARTED)
                .planNo(planNo).stepId(step.getId()).displayName(step.getName())
                .stepStatus(ExecutionStepStatus.RUNNING).build(), List.of());
        try {
            JSONObject inputs = step.getId().equals("direct")
                    ? JSONObject.of("query", request.getConversationContext().routingQuery())
                    : resolveInputs(step, request.getConversationContext().routingQuery(), completedResults);
            CapabilityExecutionResult result = capabilityExecutionService.execute(
                    new CapabilityExecutionRequest(capability, inputs, request.getExecutionContext()));
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
            persistenceService.appendStepAttempt(attempt(request, planNo, step,
                    new StepAttemptOutcome(ExecutionStepStatus.FAILED, null, STEP_FAILED_CODE), startedAt));
            publish(request, event(request, ExecutionEventType.STEP_FAILED)
                    .planNo(planNo).stepId(step.getId()).displayName(step.getName())
                    .stepStatus(ExecutionStepStatus.FAILED).message("步骤执行失败").build(), List.of());
            throw new StepFailure(step, STEP_FAILED_CODE, exception);
        }
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

    private static JSONObject resolveInputs(PlanStep step, String query, List<PlanStepResult> completedResults) {
        Map<String, PlanStepResult> results = completedResults.stream()
                .collect(java.util.stream.Collectors.toMap(PlanStepResult::getStepId, result -> result));
        JSONObject inputs = new JSONObject();
        for (StepInputReference input : step.inputs()) {
            if (input.getSource() == StepInputSource.USER_QUERY) {
                inputs.put(input.getName(), query);
            } else if (input.getSource() == StepInputSource.LITERAL) {
                inputs.put(input.getName(), input.getValue());
            } else {
                PlanStepResult result = results.get(input.getStepId());
                if (result == null) {
                    throw new StepFailure(step, "STEP_OUTPUT_MISSING");
                }
                JSONObject output = normalizeOutput(result.getContent());
                if (!output.containsKey(input.getValue())) {
                    throw new StepFailure(step, "STEP_OUTPUT_FIELD_MISSING");
                }
                inputs.put(input.getName(), output.get(input.getValue()));
            }
        }
        return inputs;
    }

    private static JSONObject normalizeOutput(String content) {
        if (content != null && content.trim().startsWith("{")) {
            try {
                return JSON.parseObject(content);
            } catch (RuntimeException exception) {
                throw invalid("步骤输出JSON格式无效");
            }
        }
        return JSONObject.of("response", content, "result", content);
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

    private static void ensureActive(PlanExecutionRequest request, PlannerPolicy policy) {
        if (request.getExecutionContext().isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Plan-and-Execute已取消");
        }
        if (Duration.between(request.getStartedAt(), Instant.now()).toSeconds() > policy.getTimeoutSeconds()) {
            throw invalid("Plan-and-Execute超过总超时");
        }
    }

    private static void requireRequest(PlanExecutionRequest request) {
        if (request == null || request.getRunId() == null || request.getRunId().isBlank()
                || request.getInitialDecision() == null || request.getConversationContext() == null
                || request.getExecutionContext() == null || request.getStartedAt() == null
                || request.getInitialPlannerModelCalls() == null || request.getInitialPlannerModelCalls() < 1) {
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
        private final String errorCode;

        private StepFailure(PlanStep step, String errorCode) {
            this(step, errorCode, null);
        }

        private StepFailure(PlanStep step, String errorCode, Throwable cause) {
            super(errorCode, cause);
            this.step = Objects.requireNonNull(step);
            this.errorCode = errorCode;
        }

        private String observation() {
            return "stepId=" + step.getId() + ", capabilityCode=" + step.getCapabilityCode()
                    + ", errorCode=" + errorCode;
        }
    }
}
