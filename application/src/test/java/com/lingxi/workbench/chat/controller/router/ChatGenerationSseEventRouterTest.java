package com.lingxi.workbench.chat.controller.router;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionStepStatus;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatPlanStepBO;
import com.lingxi.workbench.chat.model.bo.ChatStepOutputEventBO;
import com.lingxi.workbench.chat.model.vo.ChatExecutionTimelineVO;
import com.lingxi.workbench.chat.model.vo.ChatErrorVO;
import com.lingxi.workbench.chat.model.vo.ChatSubAgentEventVO;
import com.lingxi.workbench.chat.model.vo.ChatStepOutputVO;
import com.lingxi.workbench.chat.model.vo.ChatToolEventVO;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import java.time.OffsetDateTime;
import java.util.List;

class ChatGenerationSseEventRouterTest {

    @Test
    void shouldRouteDelegationToolAsSubAgentEvent() {
        ChatGenerationEventBO event = ChatGenerationEventBO.tool(7, AgentToolExecutionEvent.builder()
                .invocationId("delegation-1")
                .toolCode("delegate_agent_30")
                .displayName("委派给售后专家")
                .status(AgentToolExecutionStatus.RUNNING)
                .build());

        ServerSentEvent<?> routed = ChatGenerationSseEventRouter.rout(event);

        assertEquals("subagent", routed.event());
        assertEquals("7", routed.id());
        ChatSubAgentEventVO payload = assertInstanceOf(ChatSubAgentEventVO.class, routed.data());
        assertEquals("delegation-1", payload.getDelegationId());
        assertEquals("30", payload.getAgentId());
        assertEquals("售后专家", payload.getAgentName());
        assertEquals("RUNNING", payload.getStatus());
    }

    @Test
    void shouldKeepLocalToolAsToolEvent() {
        ChatGenerationEventBO event = ChatGenerationEventBO.tool(8, AgentToolExecutionEvent.builder()
                .invocationId("tool-1")
                .toolCode("system_calculator")
                .displayName("基础计算器")
                .status(AgentToolExecutionStatus.SUCCEEDED)
                .durationMillis(15L)
                .build());

        ServerSentEvent<?> routed = ChatGenerationSseEventRouter.rout(event);

        assertEquals("tool", routed.event());
        ChatToolEventVO payload = assertInstanceOf(ChatToolEventVO.class, routed.data());
        assertEquals("system_calculator", payload.getToolCode());
        assertEquals(15L, payload.getDurationMillis());
    }

    @Test
    void shouldRouteClarificationEvent() {
        ChatClarificationEventBO clarification = ChatClarificationEventBO.builder()
                .clarificationId("99")
                .question("请选择办理事项")
                .options(List.of(new ChatClarificationEventBO.ClarificationOptionBO("refund", "申请退款")))
                .round(1)
                .expiresAt(OffsetDateTime.now().plusMinutes(10))
                .sequence(9)
                .build();

        ServerSentEvent<?> routed = ChatGenerationSseEventRouter.rout(
                ChatGenerationEventBO.clarification(9, clarification));

        assertEquals("clarification", routed.event());
        assertEquals("9", routed.id());
        assertEquals(clarification, routed.data());
    }

    @Test
    void shouldRouteSafePlanTimelineWithoutExecutionInternals() {
        ChatExecutionTimelineEventBO timeline = ChatExecutionTimelineEventBO.builder()
                .runId("run-1")
                .eventType(ExecutionEventType.PLAN_CREATED)
                .planNo(1)
                .displayName("退款处理计划")
                .stepStatus(ExecutionStepStatus.RUNNING)
                .steps(List.of(new ChatPlanStepBO("collect", "收集退款资料", "refund_agent")))
                .build();

        ServerSentEvent<?> routed = ChatGenerationSseEventRouter.rout(
                ChatGenerationEventBO.execution(10, com.lingxi.workbench.chat.common.enums.ChatGenerationEventType.PLAN, timeline));

        assertEquals("plan", routed.event());
        assertEquals("10", routed.id());
        ChatExecutionTimelineVO payload = assertInstanceOf(ChatExecutionTimelineVO.class, routed.data());
        assertEquals("run-1", payload.getRunId());
        assertEquals("PLAN_CREATED", payload.getEventType());
        assertEquals("refund_agent", payload.getSteps().getFirst().capabilityCode());
    }

    @Test
    void shouldRouteAgentLoopLimitWithSafeMessage() {
        ServerSentEvent<?> routed = ChatGenerationSseEventRouter.rout(ChatGenerationEventBO.failed(
                "AGENT_TOOL_CALL_LIMIT", "工具调用次数已达到上限，现有信息不足以继续处理"));

        assertEquals("error", routed.event());
        ChatErrorVO payload = assertInstanceOf(ChatErrorVO.class, routed.data());
        assertEquals("AGENT_TOOL_CALL_LIMIT", payload.code());
        assertEquals("工具调用次数已达到上限，现有信息不足以继续处理", payload.message());
    }

    @Test
    void shouldRoutePlanStepOutputSeparatelyFromAssistantMessage() {
        ChatGenerationEventBO event = ChatGenerationEventBO.stepOutput(
                11, new ChatStepOutputEventBO("run-1", 2, "analyze", "正在整理证据"));

        ServerSentEvent<?> routed = ChatGenerationSseEventRouter.rout(event);

        assertEquals("step_output", routed.event());
        assertEquals("11", routed.id());
        ChatStepOutputVO payload = assertInstanceOf(ChatStepOutputVO.class, routed.data());
        assertEquals("run-1", payload.getRunId());
        assertEquals(2, payload.getPlanNo());
        assertEquals("analyze", payload.getStepId());
        assertEquals("正在整理证据", payload.getContent());
    }
}
