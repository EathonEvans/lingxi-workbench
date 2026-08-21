package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.agentruntime.event.ExecutionEvent;
import com.lingxi.workbench.agentruntime.event.ExecutionEventPayload;
import com.lingxi.workbench.agentruntime.event.RuntimeEventSink;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionSkillActivationCommand;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.bo.ChatInteractionEventBO;
import com.lingxi.workbench.chat.model.bo.ChatPlanStepBO;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentProgressPhase;
import com.lingxi.workbench.infra.ai.agent.model.AgentProgressEvent;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 创建“先持久化 Runtime 事件，再转发 SSE”的 Application 事件适配器。 */
@Component
@RequiredArgsConstructor
public class ApplicationRuntimeEventSinkFactory {

    private final ExecutionPersistenceService persistenceService;

    public RuntimeEventSink create(ChatExecutionContext execution) {
        return new ApplicationRuntimeEventSink(execution, persistenceService);
    }

    private static final class ApplicationRuntimeEventSink implements RuntimeEventSink {

        private final ChatExecutionContext execution;
        private final ExecutionPersistenceService persistenceService;

        private ApplicationRuntimeEventSink(ChatExecutionContext execution,
                                            ExecutionPersistenceService persistenceService) {
            this.execution = execution;
            this.persistenceService = persistenceService;
        }

        @Override
        public void onEvent(ExecutionEvent event) {
            ExecutionEventType eventType = ExecutionEventType.valueOf(event.getType().name());
            String message = event.getPayload() == null ? null : event.getPayload().message();
            reserveApplicationBudget(event);
            ExecutionEventCommand command = ExecutionEventCommand.builder()
                    .runId(event.getRunId())
                    .sequenceNo(event.getSequenceNo())
                    .occurredAt(event.getOccurredAt())
                    .eventType(eventType)
                    .planNo(planNo(event))
                    .stepId(stepId(event))
                    .stepStatus(stepStatus(event))
                    .displayName(eventType.getDescribe())
                    .message(message)
                    .build();
            if (event.getPayload() instanceof ExecutionEventPayload.Skill skill) {
                persistenceService.appendSkillActivationEvent(command,
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
                persistenceService.appendEvent(command);
            }
            publishProjection(event);
        }

        /** Runtime是Agent局部预算真相；Application只在统一开始事件到达时占用全局额度。 */
        private void reserveApplicationBudget(ExecutionEvent event) {
            if (event.getType() == com.lingxi.workbench.agentruntime.event.ExecutionEventType.MODEL_STARTED
                    && event.getPayload() instanceof ExecutionEventPayload.Model model) {
                if (model.finalCall()) {
                    execution.reserveFinalModelCall();
                } else {
                    execution.reserveModelCall();
                }
            }
            if (event.getType() == com.lingxi.workbench.agentruntime.event.ExecutionEventType.TOOL_STARTED
                    || event.getType() == com.lingxi.workbench.agentruntime.event.ExecutionEventType.TOOL_REUSED) {
                execution.reserveToolCalls(1);
            }
        }

        /** 统一事件持久化成功后，再投影为现有Timeline、Progress与Tool SSE契约。 */
        private void publishProjection(ExecutionEvent event) {
            ExecutionEventType eventType = ExecutionEventType.valueOf(event.getType().name());
            String message = event.getPayload() == null ? null : event.getPayload().message();
            ChatExecutionTimelineEventBO.ChatExecutionTimelineEventBOBuilder timeline =
                    ChatExecutionTimelineEventBO.builder()
                    .runId(event.getRunId())
                    .eventType(eventType)
                    .displayName(eventType.getDescribe())
                    .message(message);
            if (event.getPayload() instanceof ExecutionEventPayload.Plan plan) {
                timeline.planNo(plan.getPlanNo());
                if (plan.getDecision() != null && plan.getDecision().getPlan() != null) {
                    timeline.displayName(plan.getDecision().getPlan().getTitle())
                            .steps(plan.getDecision().getPlan().steps().stream()
                                    .map(step -> new ChatPlanStepBO(step.getId(), step.getName(),
                                            step.getCapabilityCode()))
                                    .toList());
                }
            } else if (event.getPayload() instanceof ExecutionEventPayload.Step step) {
                timeline.planNo(step.getPlanNo())
                        .stepId(step.getStep().getId())
                        .displayName(step.getStep().getName())
                        .stepStatus(ExecutionStepStatus.valueOf(step.getStatus().name()));
            } else if (event.getPayload() instanceof ExecutionEventPayload.Clarification clarification) {
                timeline.planNo(clarification.getPlanNo());
            }
            execution.getEventPublisher().publishExecution(timeline.build());
            if (event.getPayload() instanceof ExecutionEventPayload.Model model
                    && eventType == ExecutionEventType.MODEL_STARTED) {
                AgentProgressPhase phase = model.finalCall()
                        ? AgentProgressPhase.FINALIZING
                        : model.callNo() == 1 ? AgentProgressPhase.ANALYZING : AgentProgressPhase.EVALUATING;
                publishProgress(phase, message, model.callNo());
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

        private static Integer planNo(ExecutionEvent event) {
            if (event.getPayload() instanceof ExecutionEventPayload.Plan plan) {
                return plan.getPlanNo();
            }
            if (event.getPayload() instanceof ExecutionEventPayload.Step step) {
                return step.getPlanNo();
            }
            return event.getPayload() instanceof ExecutionEventPayload.Clarification clarification
                    ? clarification.getPlanNo() : null;
        }

        private static String stepId(ExecutionEvent event) {
            return event.getPayload() instanceof ExecutionEventPayload.Step step
                    ? step.getStep().getId() : null;
        }

        private static ExecutionStepStatus stepStatus(ExecutionEvent event) {
            return event.getPayload() instanceof ExecutionEventPayload.Step step
                    ? ExecutionStepStatus.valueOf(step.getStatus().name()) : null;
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
            execution.getEventPublisher().publishInteractionResolved(event);
        }

        private void publishProgress(AgentProgressPhase phase, String message, int roundNo) {
            int toolCallCount = execution.getRuntimePolicy().getMaxToolCalls() - execution.remainingToolCalls();
            execution.getEventPublisher().publishProgress(new AgentProgressEvent(
                    phase, message, roundNo, toolCallCount));
        }

        private void publishTool(ExecutionEvent event, ExecutionEventPayload.Tool tool) {
            execution.getEventPublisher().publishTool(AgentToolExecutionEvent.builder()
                    .invocationId(event.getInvocationId())
                    .toolCode(tool.toolCode())
                    .displayName(tool.displayName())
                    .status(toolStatus(event.getType()))
                    .durationMillis(tool.durationMillis())
                    .errorCode(tool.errorCode())
                    .build());
        }

        private static AgentToolExecutionStatus toolStatus(
                com.lingxi.workbench.agentruntime.event.ExecutionEventType type) {
            return switch (type) {
                case TOOL_STARTED -> AgentToolExecutionStatus.RUNNING;
                case TOOL_REUSED -> AgentToolExecutionStatus.REUSED;
                case TOOL_COMPLETED -> AgentToolExecutionStatus.SUCCEEDED;
                case TOOL_FAILED -> AgentToolExecutionStatus.FAILED;
                default -> throw new IllegalArgumentException("非Tool事件不能投影为Tool状态：" + type);
            };
        }

        @Override
        public void onText(ChatStreamChunk chunk) {
            if (!chunk.content().isEmpty()) {
                execution.markOutputActivity();
            }
            execution.recordUsage(chunk);
            execution.getEventPublisher().publishText(chunk);
        }

        @Override
        public boolean isCancelled() {
            return execution.isCancelled();
        }
    }
}
