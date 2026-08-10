package com.lingxi.workbench.chat.controller.router;

import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.bo.ChatErrorBO;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.vo.ChatChunkVO;
import com.lingxi.workbench.chat.model.vo.ChatErrorVO;
import com.lingxi.workbench.chat.model.vo.ChatExecutionTimelineVO;
import com.lingxi.workbench.chat.model.vo.ChatSnapshotVO;
import com.lingxi.workbench.chat.model.vo.ChatSubAgentEventVO;
import com.lingxi.workbench.chat.model.vo.ChatToolEventVO;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import org.springframework.http.codec.ServerSentEvent;

import java.util.Objects;

import static com.lingxi.workbench.application.runtime.common.constants.AgentChatConstants.DELEGATE_AGENT_TOOL_PREFIX;
import static com.lingxi.workbench.chat.common.constants.ChatErrorConstants.GENERATION_CANCELLED_CODE;
import static com.lingxi.workbench.chat.common.constants.ChatErrorConstants.GENERATION_CANCELLED_MESSAGE;
import static com.lingxi.workbench.chat.common.constants.ChatErrorConstants.MODEL_ERROR_CLIENT_MESSAGE;
import static com.lingxi.workbench.chat.common.constants.ChatErrorConstants.MODEL_ERROR_CODE;

/**
 * 将内部生成事件转换为对外 SSE 协议事件。
 *
 * <p>该转换器只处理传输协议映射，不查询数据、不修改生成状态，也不承担接口权限校验。</p>
 */
public final class ChatGenerationSseEventRouter {

    private ChatGenerationSseEventRouter() {
    }

    /**
     * 根据内部事件类型构建对应的 SSE 事件。
     */
    public static ServerSentEvent<?> rout(ChatGenerationEventBO event) {
        return switch (event.type()) {
            case CHUNK -> messageEvent(event);
            case TOOL -> executionEvent(event);
            case CLARIFICATION -> clarificationEvent(event);
            case PLANNING, PLAN, STEP, REPLANNING -> timelineEvent(event);
            case SNAPSHOT -> snapshotEvent(event.content());
            case COMPLETED -> doneEvent();
            case FAILED -> errorEvent(event);
            case CANCELLED -> cancelledEvent();
        };
    }

    private static ServerSentEvent<ChatExecutionTimelineVO> timelineEvent(ChatGenerationEventBO event) {
        ChatExecutionTimelineEventBO execution = Objects.requireNonNull(event.execution(), "执行时间线内容不能为空");
        ChatExecutionTimelineVO payload = ChatExecutionTimelineVO.builder()
                .sequence(event.sequence())
                .runId(execution.getRunId())
                .eventType(execution.getEventType().getCode())
                .planNo(execution.getPlanNo())
                .stepId(execution.getStepId())
                .displayName(execution.getDisplayName())
                .stepStatus(execution.getStepStatus() == null ? null : execution.getStepStatus().getCode())
                .message(execution.getMessage())
                .steps(execution.steps().stream()
                        .map(step -> new ChatExecutionTimelineVO.PlanStepVO(
                                step.stepId(), step.name(), step.capabilityCode()))
                        .toList())
                .build();
        return ServerSentEvent.<ChatExecutionTimelineVO>builder()
                .id(String.valueOf(event.sequence()))
                .event(eventName(event.type()))
                .data(payload)
                .build();
    }

    private static String eventName(ChatGenerationEventType type) {
        return switch (type) {
            case PLANNING -> "planning";
            case PLAN -> "plan";
            case STEP -> "step";
            case REPLANNING -> "replanning";
            default -> throw new IllegalArgumentException("不是执行时间线事件：" + type);
        };
    }

    private static ServerSentEvent<ChatClarificationEventBO> clarificationEvent(ChatGenerationEventBO event) {
        return ServerSentEvent.<ChatClarificationEventBO>builder()
                .id(String.valueOf(event.sequence()))
                .event("clarification")
                .data(Objects.requireNonNull(event.clarification(), "澄清事件内容不能为空"))
                .build();
    }

    /**
     * 将成员 Agent 委派从普通工具状态中分离，避免前端重复展示同一次执行。
     */
    private static ServerSentEvent<?> executionEvent(ChatGenerationEventBO event) {
        AgentToolExecutionEvent tool = Objects.requireNonNull(event.tool(), "工具事件内容不能为空");
        return isDelegation(tool) ? subAgentEvent(event, tool) : toolEvent(event, tool);
    }

    /**
     * 构建文本增量事件，携带自增序号用于客户端恢复订阅。
     */
    private static ServerSentEvent<ChatChunkVO> messageEvent(ChatGenerationEventBO event) {
        return ServerSentEvent.<ChatChunkVO>builder()
                .id(String.valueOf(event.sequence()))
                .event("message")
                .data(new ChatChunkVO(event.sequence(), event.content()))
                .build();
    }

    /**
     * 构建工具执行状态事件，不暴露工具参数和原始结果。
     */
    private static ServerSentEvent<ChatToolEventVO> toolEvent(ChatGenerationEventBO event, AgentToolExecutionEvent tool) {
        ChatToolEventVO payload = ChatToolEventVO.builder()
                .sequence(event.sequence())
                .invocationId(tool.getInvocationId())
                .toolCode(tool.getToolCode())
                .displayName(tool.getDisplayName())
                .status(tool.getStatus().getCode())
                .durationMillis(tool.getDurationMillis())
                .errorCode(tool.getErrorCode())
                .build();
        return ServerSentEvent.<ChatToolEventVO>builder()
                .id(String.valueOf(event.sequence()))
                .event("tool")
                .data(payload)
                .build();
    }

    /**
     * 构建子 Agent 委派事件，不暴露委派任务和成员 Agent 原始回答。
     */
    private static ServerSentEvent<ChatSubAgentEventVO> subAgentEvent(ChatGenerationEventBO event, AgentToolExecutionEvent tool) {
        String agentId = tool.getToolCode().substring(DELEGATE_AGENT_TOOL_PREFIX.length());
        String displayName = Objects.requireNonNullElse(tool.getDisplayName(), "成员Agent");
        String agentName = displayName.startsWith("委派给")
                ? displayName.substring("委派给".length())
                : displayName;
        ChatSubAgentEventVO payload = ChatSubAgentEventVO.builder()
                .sequence(event.sequence())
                .delegationId(tool.getInvocationId())
                .agentId(agentId)
                .agentName(agentName)
                .status(tool.getStatus().getCode())
                .durationMillis(tool.getDurationMillis())
                .errorCode(tool.getErrorCode())
                .build();
        return ServerSentEvent.<ChatSubAgentEventVO>builder()
                .id(String.valueOf(event.sequence()))
                .event("subagent")
                .data(payload)
                .build();
    }

    private static boolean isDelegation(AgentToolExecutionEvent tool) {
        return Objects.nonNull(tool.getToolCode())
                && tool.getToolCode().startsWith(DELEGATE_AGENT_TOOL_PREFIX)
                && tool.getToolCode().length() > DELEGATE_AGENT_TOOL_PREFIX.length();
    }

    /**
     * 构建完整回答快照事件，用于续接时快速恢复已生成文本。
     */
    private static ServerSentEvent<ChatSnapshotVO> snapshotEvent(String content) {
        return ServerSentEvent.<ChatSnapshotVO>builder()
                .event("snapshot")
                .data(new ChatSnapshotVO(content))
                .build();
    }

    /**
     * 构建生成完成事件。
     */
    private static ServerSentEvent<Void> doneEvent() {
        return ServerSentEvent.<Void>builder().event("done").build();
    }

    /**
     * 构建模型调用失败事件。
     */
    private static ServerSentEvent<ChatErrorVO> errorEvent(ChatGenerationEventBO event) {
        ChatErrorBO error = Objects.requireNonNullElseGet(event.error(), () -> new ChatErrorBO(MODEL_ERROR_CODE, MODEL_ERROR_CLIENT_MESSAGE));
        return ServerSentEvent.<ChatErrorVO>builder()
                .event("error")
                .data(new ChatErrorVO(error.code(), error.message()))
                .build();
    }

    /**
     * 构建用户取消生成事件。
     */
    private static ServerSentEvent<ChatErrorVO> cancelledEvent() {
        return ServerSentEvent.<ChatErrorVO>builder()
                .event("cancelled")
                .data(new ChatErrorVO(GENERATION_CANCELLED_CODE, GENERATION_CANCELLED_MESSAGE))
                .build();
    }
}
