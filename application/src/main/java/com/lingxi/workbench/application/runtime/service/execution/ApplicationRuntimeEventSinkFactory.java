package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventPayload;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.ApplicationExecutionBudget;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionSkillActivationCommand;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.bo.ChatInteractionEventBO;
import com.lingxi.workbench.chat.model.bo.ChatPlanStepBO;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentProgressPhase;
import com.lingxi.workbench.infra.ai.agent.model.AgentProgressEvent;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.DELEGATE_AGENT_TOOL_PREFIX;

/** 创建“先持久化 Runtime 事件，再转发 SSE”的 Application 事件适配器。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApplicationRuntimeEventSinkFactory {

    private final ExecutionPersistenceService persistenceService;
    private final ObservationRegistry observationRegistry;

    public RuntimeEventSink create(ApplicationExecutionBudget executionBudget,
                                   ChatExecutionEventPublisher eventPublisher) {
        return new ApplicationRuntimeEventSink(executionBudget, eventPublisher, persistenceService,
                observationRegistry, observationRegistry.getCurrentObservation());
    }

    private static final class ApplicationRuntimeEventSink implements RuntimeEventSink {

        private final ApplicationExecutionBudget executionBudget;
        private final ChatExecutionEventPublisher eventPublisher;
        private final ExecutionPersistenceService persistenceService;
        private final RuntimePhaseTracker phaseTracker;

        private ApplicationRuntimeEventSink(ApplicationExecutionBudget executionBudget,
                                            ChatExecutionEventPublisher eventPublisher,
                                            ExecutionPersistenceService persistenceService,
                                            ObservationRegistry observationRegistry,
                                            Observation parentObservation) {
            this.executionBudget = executionBudget;
            this.eventPublisher = eventPublisher;
            this.persistenceService = persistenceService;
            this.phaseTracker = new RuntimePhaseTracker(observationRegistry, parentObservation);
        }

        @Override
        public long onEvent(ExecutionEvent event) {
            ExecutionEventType eventType = ExecutionEventType.valueOf(event.getType().name());
            String message = userMessage(event, eventType);
            reserveApplicationBudget(event);
            ExecutionEventCommand command = ExecutionEventCommand.builder()
                    .runId(event.getRunId())
                    .occurredAt(event.getOccurredAt())
                    .eventType(eventType)
                    .planNo(planNo(event))
                    .stepId(stepId(event))
                    .stepStatus(stepStatus(event))
                    .displayName(eventType.getDescribe())
                    .message(message)
                    .build();
            ExecutionEventEntity persisted;
            if (event.getPayload() instanceof ExecutionEventPayload.Skill skill) {
                persisted = persistenceService.appendSkillActivationEvent(command,
                        ExecutionSkillActivationCommand.builder()
                                .runId(event.getRunId())
                                .agentVersionId(event.getAgentVersionId())
                                .skillId(skill.getSkillId())
                                .skillVersionId(skill.getSkillVersionId())
                                .skillCode(skill.getSkillCode())
                                .checksum(skill.getChecksum())
                                .reason(skill.getReason())
                                .effectiveToolCodes(skill.getEffectiveToolCodes())
                                .build());
            } else {
                persisted = persistenceService.appendEvent(command);
            }
            phaseTracker.accept(event);
            publishProjection(event);
            return persisted.getSequenceNo();
        }

        /** Runtime是Agent局部预算真相；Application只在统一开始事件到达时占用全局额度。 */
        private void reserveApplicationBudget(ExecutionEvent event) {
            if (event.getType() == com.lingxi.workbench.agentruntime.event.ExecutionEventType.MODEL_STARTED
                    && event.getPayload() instanceof ExecutionEventPayload.Model model) {
                if (model.finalCall()) {
                    executionBudget.reserveFinalModelCall();
                } else {
                    executionBudget.reserveModelCall();
                }
            }
            if (event.getType() == com.lingxi.workbench.agentruntime.event.ExecutionEventType.TOOL_STARTED
                    || event.getType() == com.lingxi.workbench.agentruntime.event.ExecutionEventType.TOOL_REUSED) {
                executionBudget.reserveToolCalls(1);
            }
        }

        /** 统一事件持久化成功后，再投影为现有Timeline、Progress与Tool SSE契约。 */
        private void publishProjection(ExecutionEvent event) {
            ExecutionEventType eventType = ExecutionEventType.valueOf(event.getType().name());
            if (isCoordination(eventType)) {
                return;
            }
            String message = userMessage(event, eventType);
            ChatExecutionTimelineEventBO.ChatExecutionTimelineEventBOBuilder timeline =
                    ChatExecutionTimelineEventBO.builder()
                    .runId(event.getRunId())
                    .eventType(eventType)
                    .displayName(eventType.getDescribe())
                    .message(message);
            if (event.getPayload() instanceof ExecutionEventPayload.Plan plan) {
                timeline.planNo(plan.getPlanNo());
                if (!plan.getTasks().isEmpty()) {
                    timeline.steps(plan.getTasks().stream()
                            .map(task -> new ChatPlanStepBO(task.getId(), task.getSubject(),
                                    taskCapabilityCode(task), taskStatus(task)))
                            .toList());
                }
            } else if (event.getPayload() instanceof ExecutionEventPayload.Step step) {
                timeline.planNo(step.getTaskRevision())
                        .stepId(step.getStep().getId())
                        .displayName(step.getStep().getName())
                        .stepStatus(ExecutionStepStatus.valueOf(step.getStatus().name()));
            }
            eventPublisher.publishExecution(timeline.build());
            if (event.getPayload() instanceof ExecutionEventPayload.Model model
                    && eventType == ExecutionEventType.MODEL_STARTED) {
                AgentProgressPhase phase = model.finalCall()
                        ? AgentProgressPhase.FINALIZING
                        : model.callNo() == 1 ? AgentProgressPhase.ANALYZING : AgentProgressPhase.EVALUATING;
                publishProgress(phase, phase.getDescribe(), model.callNo());
            } else if (event.getPayload() instanceof ExecutionEventPayload.Tool tool) {
                publishTool(event, tool);
                if (eventType == ExecutionEventType.TOOL_STARTED) {
                    publishProgress(AgentProgressPhase.USING_TOOLS, "正在执行所需操作", 0);
                }
            } else if (eventType == ExecutionEventType.FINALIZING) {
                publishProgress(AgentProgressPhase.FINALIZING, message, 0);
            } else if (event.getPayload() instanceof ExecutionEventPayload.Human human) {
                publishHuman(eventType, human);
            }
        }

        /** 内部事件保留技术事实，对话侧只接收面向用户的安全过程文案。 */
        private static String userMessage(ExecutionEvent event, ExecutionEventType eventType) {
            return switch (eventType) {
                case RUN_STARTED -> "开始处理你的问题";
                case AGENT_STARTED -> "开始处理当前任务";
                case AGENT_COMPLETED -> "当前任务处理完成";
                case AGENT_PARTIAL -> "当前任务已完成部分内容";
                case AGENT_FAILED -> "当前任务处理失败";
                case AGENT_CANCELLED -> "当前任务已停止";
                case SKILL_ACTIVATED -> "已准备相关专业能力";
                case MODEL_STARTED -> modelStartedMessage(event);
                case MODEL_COMPLETED -> "本轮分析已完成";
                case TOOL_STARTED -> "所需操作已开始";
                case TOOL_REUSED -> "已复用相同操作结果";
                case TOOL_COMPLETED -> "所需操作已完成";
                case TOOL_FAILED -> "所需操作失败";
                case TOOL_WAITING_HUMAN -> "等待你确认后继续";
                case COORDINATION_STARTED, COORDINATION_COMPLETED, COORDINATION_FAILED -> payloadMessage(event);
                case HUMAN_REQUIRED -> "等待你的确认";
                case HUMAN_RESOLVED -> "确认已生效，继续处理";
                case PLANNING -> "正在规划任务";
                case PLAN_CREATED -> "任务计划已更新";
                case STEP_STARTED -> "当前步骤已开始";
                case STEP_REUSED -> "已复用当前步骤结果";
                case STEP_COMPLETED -> "当前步骤已完成";
                case STEP_FAILED -> "当前步骤处理失败";
                case REPLANNING -> "正在调整执行计划";
                case FINALIZING -> "正在基于现有信息整理回答";
                case CLARIFICATION_REQUIRED -> payloadMessage(event);
                case RUN_COMPLETED -> "处理完成";
                case RUN_PARTIAL -> "已完成部分内容";
                case RUN_RECONCILIATION_PENDING -> "操作结果等待确认";
                case RUN_FAILED -> "处理失败";
                case RUN_CANCELLED -> "处理已停止";
            };
        }

        private static String modelStartedMessage(ExecutionEvent event) {
            if (!(event.getPayload() instanceof ExecutionEventPayload.Model model)) {
                return "正在分析问题";
            }
            if (model.finalCall()) {
                return "正在整理回答";
            }
            return model.callNo() == 1 ? "正在分析问题" : "正在结合已有信息继续处理";
        }

        private static String payloadMessage(ExecutionEvent event) {
            return event.getPayload() == null ? null : event.getPayload().message();
        }

        private static boolean isCoordination(ExecutionEventType eventType) {
            return eventType == ExecutionEventType.COORDINATION_STARTED
                    || eventType == ExecutionEventType.COORDINATION_COMPLETED
                    || eventType == ExecutionEventType.COORDINATION_FAILED;
        }

        private static Integer planNo(ExecutionEvent event) {
            if (event.getPayload() instanceof ExecutionEventPayload.Plan plan) {
                return plan.getPlanNo();
            }
            if (event.getPayload() instanceof ExecutionEventPayload.Step step) {
                return step.getTaskRevision();
            }
            if (event.getPayload() instanceof ExecutionEventPayload.Tool tool && tool.isAgentDelegation()) {
                return tool.getTaskRevision();
            }
            return null;
        }

        private static String stepId(ExecutionEvent event) {
            if (event.getPayload() instanceof ExecutionEventPayload.Step step) {
                return step.getStep().getId();
            }
            if (event.getPayload() instanceof ExecutionEventPayload.Tool tool && tool.isAgentDelegation()) {
                return tool.getStepId();
            }
            return null;
        }

        private static ExecutionStepStatus stepStatus(ExecutionEvent event) {
            return event.getPayload() instanceof ExecutionEventPayload.Step step
                    ? ExecutionStepStatus.valueOf(step.getStatus().name()) : null;
        }

        private static String taskCapabilityCode(io.agentscope.core.state.Task task) {
            Object value = task.getMetadata().get("capabilityCode");
            return value instanceof String code ? code : null;
        }

        private static ExecutionStepStatus taskStatus(io.agentscope.core.state.Task task) {
            return switch (task.getState()) {
                case PENDING -> ExecutionStepStatus.PENDING;
                case IN_PROGRESS -> ExecutionStepStatus.RUNNING;
                case COMPLETED -> ExecutionStepStatus.COMPLETED;
            };
        }

        private void publishHuman(ExecutionEventType type, ExecutionEventPayload.Human human) {
            if (type == ExecutionEventType.HUMAN_REQUIRED) {
                // Assistant必须先落为WAITING_HUMAN，再由Chat入口发布交互卡片，避免前端先看到可操作事件。
                return;
            }
            com.lingxi.workbench.agentruntime.checkpoint.RuntimeInterruption interruption =
                    human.getInterruption();
            ChatInteractionEventBO event = ChatInteractionEventBO.builder()
                    .interactionId(String.valueOf(interruption.getInteractionId()))
                    .interactionType("ACTION_APPROVAL")
                    .title(interruption.getTitle())
                    .summary(interruption.getSummary())
                    .riskLevel(interruption.getRiskLevel())
                    .resourceScope(interruption.getResourceScope())
                    .fingerprint(interruption.getFingerprint())
                    .revision(interruption.getRevision())
                    .expiresAt(interruption.getExpiresAt())
                    .build();
            eventPublisher.publishInteractionResolved(event);
        }

        private void publishProgress(AgentProgressPhase phase, String message, int roundNo) {
            int toolCallCount = executionBudget.runtimePolicy().getMaxToolCalls()
                    - executionBudget.remainingToolCalls();
            eventPublisher.publishProgress(new AgentProgressEvent(
                    phase, message, roundNo, toolCallCount));
        }

        private void publishTool(ExecutionEvent event, ExecutionEventPayload.Tool tool) {
            String toolCode = tool.isAgentDelegation()
                    ? DELEGATE_AGENT_TOOL_PREFIX + tool.getDelegatedAgentId() : tool.toolCode();
            eventPublisher.publishTool(AgentToolExecutionEvent.builder()
                    .invocationId(event.getInvocationId())
                    .toolCode(toolCode)
                    .displayName(tool.displayName())
                    .status(toolStatus(event.getType()))
                    .durationMillis(tool.durationMillis())
                    .errorCode(tool.errorCode())
                    .planNo(tool.getTaskRevision())
                    .stepId(tool.getStepId())
                    .build());
        }

        private static AgentToolExecutionStatus toolStatus(
                com.lingxi.workbench.agentruntime.event.ExecutionEventType type) {
            return switch (type) {
                case TOOL_STARTED -> AgentToolExecutionStatus.RUNNING;
                case TOOL_REUSED -> AgentToolExecutionStatus.REUSED;
                case TOOL_COMPLETED -> AgentToolExecutionStatus.SUCCEEDED;
                case TOOL_FAILED -> AgentToolExecutionStatus.FAILED;
                case TOOL_WAITING_HUMAN -> AgentToolExecutionStatus.WAITING_HUMAN;
                default -> throw new IllegalArgumentException("非Tool事件不能投影为Tool状态：" + type);
            };
        }

        @Override
        public void onText(ChatStreamChunk chunk) {
            executionBudget.recordUsage(chunk.usage());
            eventPublisher.publishText(chunk);
        }

        @Override
        public boolean isCancelled() {
            return eventPublisher.isCancelled();
        }
    }

    /** 将 Runtime 稳定事件配对为现有 Micrometer 链路中的阶段 span。 */
    private static final class RuntimePhaseTracker {

        private static final String OBSERVATION_NAME = "lingxi.ai.runtime.phase";

        private final ObservationRegistry observationRegistry;
        private final Observation parentObservation;
        private final Map<String, Observation> activeObservations = new ConcurrentHashMap<>();

        private RuntimePhaseTracker(ObservationRegistry observationRegistry, Observation parentObservation) {
            this.observationRegistry = Objects.requireNonNull(observationRegistry, "观测注册器不能为空");
            this.parentObservation = parentObservation;
        }

        private void accept(ExecutionEvent event) {
            try {
                RuntimePhase phase = phase(event);
                if (Objects.nonNull(phase)) {
                    if (isStarted(event.getType())) {
                        start(event, phase);
                    } else {
                        stop(event, phase);
                    }
                }
                if (isAgentTerminal(event.getType())) {
                    closeIncomplete();
                }
            } catch (RuntimeException exception) {
                log.warn("Runtime阶段观测失败，runId={}, eventType={}", event.getRunId(), event.getType(), exception);
            }
        }

        private void start(ExecutionEvent event, RuntimePhase phase) {
            Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
                    .contextualName(phase.displayName())
                    .lowCardinalityKeyValue("lingxi.runtime.phase", phase.type())
                    .lowCardinalityKeyValue("lingxi.runtime.operation", phase.operation())
                    .highCardinalityKeyValue("lingxi.runtime.operation.name", phase.displayName())
                    .highCardinalityKeyValue("lingxi.run.id", event.getRunId())
                    .highCardinalityKeyValue("lingxi.agent.id", String.valueOf(event.getAgentId()))
                    .highCardinalityKeyValue("lingxi.agent.version.id", String.valueOf(event.getAgentVersionId()));
            if (Objects.nonNull(parentObservation)) {
                observation.parentObservation(parentObservation);
            }
            if (Objects.nonNull(event.getInvocationId())) {
                observation.highCardinalityKeyValue("lingxi.runtime.invocation.id", event.getInvocationId());
            }
            if (event.getPayload() instanceof ExecutionEventPayload.Model model) {
                observation.highCardinalityKeyValue("lingxi.runtime.model.call_no", String.valueOf(model.callNo()));
                observation.highCardinalityKeyValue("lingxi.runtime.model.instance_id",
                        String.valueOf(model.modelInstanceId()));
                observation.highCardinalityKeyValue("lingxi.runtime.model.code", model.modelCode());
            }
            observation.start();
            Observation previous = activeObservations.putIfAbsent(phase.key(), observation);
            if (Objects.nonNull(previous)) {
                observation.error(new IllegalStateException("RUNTIME_PHASE_DUPLICATED"));
                observation.stop();
            }
        }

        private void stop(ExecutionEvent event, RuntimePhase phase) {
            Observation observation = activeObservations.remove(phase.key());
            if (Objects.isNull(observation)) {
                return;
            }
            if (event.getPayload() instanceof ExecutionEventPayload.Model model
                    && Objects.nonNull(model.firstResponseMillis())) {
                observation.highCardinalityKeyValue("lingxi.runtime.model.first_response_ms",
                        String.valueOf(model.firstResponseMillis()));
            }
            if (event.getPayload() instanceof ExecutionEventPayload.Model model) {
                addTokenUsage(observation, model.usage());
            }
            if (event.getType() == com.lingxi.workbench.agentruntime.event.ExecutionEventType.TOOL_FAILED
                    || event.getType()
                    == com.lingxi.workbench.agentruntime.event.ExecutionEventType.COORDINATION_FAILED) {
                observation.highCardinalityKeyValue("lingxi.runtime.error.code", runtimeErrorCode(event));
                observation.error(new IllegalStateException("RUNTIME_PHASE_FAILED"));
            }
            observation.stop();
        }

        private static void addTokenUsage(Observation observation,
                                          com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage usage) {
            if (Objects.nonNull(usage.inputTokens())) {
                observation.highCardinalityKeyValue("lingxi.runtime.model.input_tokens",
                        String.valueOf(usage.inputTokens()));
            }
            if (Objects.nonNull(usage.outputTokens())) {
                observation.highCardinalityKeyValue("lingxi.runtime.model.output_tokens",
                        String.valueOf(usage.outputTokens()));
            }
        }

        private void closeIncomplete() {
            activeObservations.forEach((key, observation) -> {
                if (activeObservations.remove(key, observation)) {
                    observation.highCardinalityKeyValue("lingxi.runtime.error.code", "RUNTIME_PHASE_INCOMPLETE");
                    observation.error(new IllegalStateException("RUNTIME_PHASE_INCOMPLETE"));
                    observation.stop();
                }
            });
        }

        private static String runtimeErrorCode(ExecutionEvent event) {
            if (event.getPayload() instanceof ExecutionEventPayload.Tool tool
                    && Objects.nonNull(tool.errorCode())) {
                return tool.errorCode();
            }
            if (event.getPayload() instanceof ExecutionEventPayload.Coordination coordination
                    && Objects.nonNull(coordination.errorCode())) {
                return coordination.errorCode();
            }
            return "RUNTIME_PHASE_FAILED";
        }

        private static RuntimePhase phase(ExecutionEvent event) {
            if (event.getPayload() instanceof ExecutionEventPayload.Model model
                    && (event.getType()
                    == com.lingxi.workbench.agentruntime.event.ExecutionEventType.MODEL_STARTED
                    || event.getType()
                    == com.lingxi.workbench.agentruntime.event.ExecutionEventType.MODEL_COMPLETED)) {
                String displayName = model.finalCall() ? "回答整理" : "模型分析";
                return new RuntimePhase("MODEL:" + model.callNo(), "MODEL", "model", displayName);
            }
            if (event.getPayload() instanceof ExecutionEventPayload.Tool tool
                    && (event.getType()
                    == com.lingxi.workbench.agentruntime.event.ExecutionEventType.TOOL_STARTED
                    || event.getType()
                    == com.lingxi.workbench.agentruntime.event.ExecutionEventType.TOOL_COMPLETED
                    || event.getType()
                    == com.lingxi.workbench.agentruntime.event.ExecutionEventType.TOOL_FAILED
                    || event.getType()
                    == com.lingxi.workbench.agentruntime.event.ExecutionEventType.TOOL_WAITING_HUMAN)) {
                return new RuntimePhase("TOOL:" + event.getInvocationId(), "TOOL", tool.toolCode(),
                        tool.displayName());
            }
            if (event.getPayload() instanceof ExecutionEventPayload.Coordination coordination) {
                return new RuntimePhase("COORDINATION:" + event.getInvocationId(), "COORDINATION",
                        coordination.operation(), coordination.displayName());
            }
            return null;
        }

        private static boolean isStarted(
                com.lingxi.workbench.agentruntime.event.ExecutionEventType eventType) {
            return eventType == com.lingxi.workbench.agentruntime.event.ExecutionEventType.MODEL_STARTED
                    || eventType == com.lingxi.workbench.agentruntime.event.ExecutionEventType.TOOL_STARTED
                    || eventType
                    == com.lingxi.workbench.agentruntime.event.ExecutionEventType.COORDINATION_STARTED;
        }

        private static boolean isAgentTerminal(
                com.lingxi.workbench.agentruntime.event.ExecutionEventType eventType) {
            return eventType == com.lingxi.workbench.agentruntime.event.ExecutionEventType.AGENT_COMPLETED
                    || eventType == com.lingxi.workbench.agentruntime.event.ExecutionEventType.AGENT_FAILED
                    || eventType == com.lingxi.workbench.agentruntime.event.ExecutionEventType.AGENT_CANCELLED;
        }

        /**
         * @param key         当前事件接收器内的配对键
         * @param type        阶段类型
         * @param operation   稳定操作编码
         * @param displayName 管理端可读名称
         */
        private record RuntimePhase(String key, String type, String operation, String displayName) {
        }
    }
}
