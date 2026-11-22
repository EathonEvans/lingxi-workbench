package com.lingxi.workbench.chat.holder;

import com.lingxi.workbench.chat.common.enums.ChatGenerationEventType;
import com.lingxi.workbench.chat.model.bo.ChatGenerationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ChatStepOutputEventBO;
import com.lingxi.workbench.chat.repo.ChatGenerationEventRepo;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.tool.common.enums.AgentToolExecutionStatus;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentProgressPhase;
import com.lingxi.workbench.infra.ai.agent.model.AgentProgressEvent;
import com.lingxi.workbench.observability.service.TraceRun;
import com.lingxi.workbench.workflow.model.MemoryConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatInteractionEventBO;
import com.lingxi.workbench.chat.model.bo.ChatExecutionTimelineEventBO;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

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
                .traceRun(mock(TraceRun.class))
                .build();
        task.publishTool(toolEvent(AgentToolExecutionStatus.RUNNING, null));
        task.publishProgress(new AgentProgressEvent(
                AgentProgressPhase.USING_TOOLS, "正在执行所需操作", 1, 1));
        task.publishText(new ChatStreamChunk("回答"));
        task.publishTool(toolEvent(AgentToolExecutionStatus.SUCCEEDED, 25L));
        assertTrue(task.beginTerminal());
        task.publishTerminal(ChatGenerationEventBO.completed());

        List<ChatGenerationEventBO> events = task.eventsAfter(1).collectList().block();

        assertEquals(4, events.size());
        assertEquals(ChatGenerationEventType.PROGRESS, events.get(0).type());
        assertEquals(2, events.get(0).sequence());
        assertEquals(ChatGenerationEventType.CHUNK, events.get(1).type());
        assertEquals(3, events.get(1).sequence());
        assertEquals(ChatGenerationEventType.TOOL, events.get(2).type());
        assertEquals(4, events.get(2).sequence());
        assertEquals(AgentToolExecutionStatus.SUCCEEDED, events.get(2).tool().getStatus());
        assertEquals(ChatGenerationEventType.COMPLETED, events.get(3).type());
        assertEquals("回答", task.answer());
    }

    @Test
    void shouldPublishStepOutputWithoutAppendingAssistantAnswer() {
        RunningGenerationTask task = RunningGenerationTask.builder()
                .turn(new ChatTurnBO(10L, 11L, 12L, 1))
                .userId(7L)
                .applicationVersionId(30L)
                .memoryConfig(MemoryConfig.disabled())
                .eventRepo(mock(ChatGenerationEventRepo.class))
                .traceRun(mock(TraceRun.class))
                .build();

        task.publishStepOutput(new ChatStepOutputEventBO("run-1", 1, "collect", "步骤结果"));
        assertTrue(task.beginTerminal());
        task.publishTerminal(ChatGenerationEventBO.completed());

        List<ChatGenerationEventBO> events = task.eventsAfter(0).collectList().block();

        assertEquals(ChatGenerationEventType.STEP_OUTPUT, events.getFirst().type());
        assertEquals("步骤结果", events.getFirst().stepOutput().content());
        assertEquals("", task.answer());
    }

    @Test
    void shouldIgnoreLateFinalResultAfterCancellation() {
        RunningGenerationTask task = task(mock(ChatGenerationEventRepo.class));
        assertTrue(task.beginTerminal());
        task.publishTerminal(ChatGenerationEventBO.cancelled());

        assertFalse(task.beginCompletion("迟到回答", TokenUsage.of(20, 10, 30)));
        assertEquals("已有回答", task.answer());
        assertEquals(TokenUsage.of(5, 2, 7), task.tokenUsage());
        assertEquals(List.of(ChatGenerationEventBO.cancelled()), task.eventsAfter(0).collectList().block());
    }

    @Test
    void shouldIgnoreLateFinalResultAfterHumanPause() {
        RunningGenerationTask task = task(mock(ChatGenerationEventRepo.class));
        assertTrue(task.pauseForHuman());

        assertFalse(task.beginCompletion("迟到回答", TokenUsage.of(20, 10, 30)));
        assertEquals("已有回答", task.answer());
        assertEquals(List.of(), task.eventsAfter(0).collectList().block());
    }

    @Test
    void shouldPublishOneFinalSnapshotAndRejectSubsequentCompletion() {
        RunningGenerationTask task = task(mock(ChatGenerationEventRepo.class));
        assertTrue(task.beginCompletion("最终回答", TokenUsage.of(20, 10, 30)));
        assertFalse(task.beginCompletion("第二个回答", TokenUsage.empty()));
        task.publishTerminal(ChatGenerationEventBO.completed());

        assertEquals("最终回答", task.answer());
        assertEquals(TokenUsage.of(20, 10, 30), task.tokenUsage());
        assertEquals(List.of(ChatGenerationEventBO.snapshot("最终回答"), ChatGenerationEventBO.completed()),
                task.eventsAfter(0).collectList().block());
    }

    @Test
    void shouldClaimCompletionBeforeNotifyingSnapshotObservers() {
        RunningGenerationTask task = task(mock(ChatGenerationEventRepo.class));
        java.util.concurrent.atomic.AtomicBoolean cancellationWon = new java.util.concurrent.atomic.AtomicBoolean(true);
        task.eventsAfter(0).subscribe(event -> {
            if (event.type() == ChatGenerationEventType.SNAPSHOT) {
                cancellationWon.set(task.beginTerminal());
            }
        });

        assertTrue(task.beginCompletion("最终回答", TokenUsage.of(20, 10, 30)));
        task.publishTerminal(ChatGenerationEventBO.completed());
        assertFalse(cancellationWon.get());
    }

    @Test
    void shouldGiveCompletionOrCancellationExclusiveOwnership() throws Exception {
        RunningGenerationTask task = task(mock(ChatGenerationEventRepo.class));
        CyclicBarrier start = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<Boolean> completed = executor.submit(() -> {
                start.await(2, TimeUnit.SECONDS);
                return task.beginCompletion("最终回答", TokenUsage.of(20, 10, 30));
            });
            Future<Boolean> cancelled = executor.submit(() -> {
                start.await(2, TimeUnit.SECONDS);
                return task.beginTerminal();
            });
            boolean completionWon = completed.get(2, TimeUnit.SECONDS);
            boolean cancellationWon = cancelled.get(2, TimeUnit.SECONDS);

            assertTrue(completionWon ^ cancellationWon);
            assertEquals(completionWon ? "最终回答" : "已有回答", task.answer());
            assertEquals(completionWon ? TokenUsage.of(20, 10, 30) : TokenUsage.of(5, 2, 7), task.tokenUsage());
        }
    }

    @Test
    void shouldSequenceAllControlEventsAndDiscardThemAfterTerminal() {
        ChatGenerationEventRepo repo = mock(ChatGenerationEventRepo.class);
        RunningGenerationTask task = task(repo);
        ChatClarificationEventBO clarification = ChatClarificationEventBO.builder().question("确认意图").build();
        ChatInteractionEventBO required = ChatInteractionEventBO.builder().interactionId("required").build();
        ChatInteractionEventBO resolved = ChatInteractionEventBO.builder().interactionId("resolved").build();
        task.publishClarification(clarification);
        task.publishInteractionRequired(required);
        task.publishInteractionResolved(resolved);
        task.publishExecution(ChatExecutionTimelineEventBO.builder().eventType(ExecutionEventType.PLANNING).build());
        task.publishExecution(ChatExecutionTimelineEventBO.builder().eventType(ExecutionEventType.PLAN_CREATED).build());
        task.publishExecution(ChatExecutionTimelineEventBO.builder().eventType(ExecutionEventType.REPLANNING).build());
        assertTrue(task.beginTerminal());
        task.publishClarification(ChatClarificationEventBO.builder().question("迟到澄清").build());
        task.publishInteractionRequired(required);
        task.publishInteractionResolved(resolved);
        task.publishExecution(ChatExecutionTimelineEventBO.builder().eventType(ExecutionEventType.PLANNING).build());
        task.publishText(new ChatStreamChunk("迟到文本"));
        task.publishTool(toolEvent(AgentToolExecutionStatus.RUNNING, null));
        task.publishProgress(new AgentProgressEvent(AgentProgressPhase.USING_TOOLS, "迟到进度", 1, 1));
        task.publishStepOutput(new ChatStepOutputEventBO("run", 1, "step", "迟到步骤"));
        task.publishTerminal(ChatGenerationEventBO.completed());

        List<ChatGenerationEventBO> events = task.eventsAfter(0).collectList().block();
        assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 0L), events.stream().map(ChatGenerationEventBO::sequence).toList());
        assertEquals(List.of(ChatGenerationEventType.CLARIFICATION, ChatGenerationEventType.INTERACTION_REQUIRED,
                ChatGenerationEventType.INTERACTION_RESOLVED, ChatGenerationEventType.PLANNING,
                ChatGenerationEventType.PLAN, ChatGenerationEventType.REPLANNING, ChatGenerationEventType.COMPLETED),
                events.stream().map(ChatGenerationEventBO::type).toList());
        assertEquals(1L, clarification.getSequence());
        assertEquals(2L, required.getSequence());
        assertEquals(3L, resolved.getSequence());
        verify(repo, times(6)).append(org.mockito.ArgumentMatchers.eq(12L), org.mockito.ArgumentMatchers.any());
    }

    private static RunningGenerationTask task(ChatGenerationEventRepo repo) {
        return RunningGenerationTask.builder().turn(new ChatTurnBO(10L, 11L, 12L, 1)).userId(7L)
                .applicationVersionId(30L).memoryConfig(MemoryConfig.disabled()).eventRepo(repo)
                .traceRun(mock(TraceRun.class)).initialAnswer("已有回答").initialUsage(TokenUsage.of(5, 2, 7)).build();
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
