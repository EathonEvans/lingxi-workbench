package com.lingxi.workbench.chat.holder;

import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.repo.ChatGenerationEventRepo;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.observability.service.AiTraceRun;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RunningGenerationTaskTest {

    @Test
    void shouldSequenceAndResumeTextAndToolEventsInOneTimeline() {
        RunningGenerationTask task = RunningGenerationTask.builder()
                .turn(new ChatTurnBO(10L, 11L, 12L, 1))
                .userId(7L)
                .applicationVersionId(30L)
                .memoryConfig(MemoryConfig.disabled())
                .eventRepo(mock(ChatGenerationEventRepo.class))
                .traceRun(mock(AiTraceRun.class))
                .build();
        task.publishTool(toolEvent(AgentToolExecutionStatus.RUNNING, null));
        task.publishText(new ChatStreamChunk("回答"));
        task.publishTool(toolEvent(AgentToolExecutionStatus.SUCCEEDED, 25L));
        assertTrue(task.beginTerminal());
        task.publishTerminal(ChatGenerationEventBO.completed());

        List<ChatGenerationEventBO> events = task.eventsAfter(1).collectList().block();

        assertEquals(3, events.size());
        assertEquals(ChatGenerationEventType.CHUNK, events.get(0).type());
        assertEquals(2, events.get(0).sequence());
        assertEquals(ChatGenerationEventType.TOOL, events.get(1).type());
        assertEquals(3, events.get(1).sequence());
        assertEquals(AgentToolExecutionStatus.SUCCEEDED, events.get(1).tool().getStatus());
        assertEquals(ChatGenerationEventType.COMPLETED, events.get(2).type());
        assertEquals("回答", task.answer());
    }

    private static AgentToolExecutionEvent toolEvent(AgentToolExecutionStatus status, Long durationMillis) {
        return AgentToolExecutionEvent.builder()
                .invocationId("tool-1")
                .toolCode("system_current_time")
                .displayName("查询当前时间")
                .status(status)
                .durationMillis(durationMillis)
                .build();
    }
}
