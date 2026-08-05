package com.lingxi.workbench.chat.controller.router;

import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.vo.ChatSubAgentEventVO;
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
}
