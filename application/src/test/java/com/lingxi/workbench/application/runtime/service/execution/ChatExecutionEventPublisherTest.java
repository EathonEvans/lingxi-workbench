package com.lingxi.workbench.application.runtime.service.execution;

import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.chat.model.bo.ChatStepOutputEventBO;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentProgressPhase;
import com.lingxi.workbench.infra.ai.agent.model.AgentProgressEvent;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ChatExecutionEventPublisherTest {

    @Test
    void shouldScopeChildAgentEventsToCurrentPlanStep() {
        ChatExecutionEventPublisher delegate = mock(ChatExecutionEventPublisher.class);
        ChatExecutionEventPublisher scoped = ChatExecutionEventPublisher.forPlanStep(
                delegate, "run-1", 2, "analyze-role");

        scoped.publishText(new ChatStreamChunk("阶段结论"));
        scoped.publishTool(AgentToolExecutionEvent.builder()
                .invocationId("tool-1").toolCode("knowledge_search").displayName("知识搜索")
                .status(AgentToolExecutionStatus.RUNNING).build());
        scoped.publishExecution(ChatExecutionTimelineEventBO.builder()
                .runId("child-run").eventType(ExecutionEventType.MODEL_STARTED)
                .displayName("模型调用已开始").message("模型调用已开始").build());
        scoped.publishProgress(new AgentProgressEvent(AgentProgressPhase.ANALYZING, "正在分析", 1, 0));

        ArgumentCaptor<ChatStepOutputEventBO> outputCaptor = ArgumentCaptor.forClass(ChatStepOutputEventBO.class);
        verify(delegate).publishStepOutput(outputCaptor.capture());
        assertEquals("analyze-role", outputCaptor.getValue().stepId());
        ArgumentCaptor<AgentToolExecutionEvent> toolCaptor = ArgumentCaptor.forClass(AgentToolExecutionEvent.class);
        verify(delegate).publishTool(toolCaptor.capture());
        assertEquals(2, toolCaptor.getValue().getPlanNo());
        assertEquals("analyze-role", toolCaptor.getValue().getStepId());
        ArgumentCaptor<ChatExecutionTimelineEventBO> timelineCaptor =
                ArgumentCaptor.forClass(ChatExecutionTimelineEventBO.class);
        verify(delegate).publishExecution(timelineCaptor.capture());
        assertEquals("run-1", timelineCaptor.getValue().getRunId());
        assertEquals(2, timelineCaptor.getValue().getPlanNo());
        assertEquals("analyze-role", timelineCaptor.getValue().getStepId());
        verify(delegate, never()).publishProgress(org.mockito.ArgumentMatchers.any());
    }
}
