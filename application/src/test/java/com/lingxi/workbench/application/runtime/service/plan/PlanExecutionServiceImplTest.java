package com.lingxi.workbench.application.runtime.service.plan;

import com.lingxi.workbench.application.runtime.service.app.RuntimeVariablesInitializer;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionDecisionAction;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.StepInputSource;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeDefinition;
import com.lingxi.workbench.application.runtime.model.AgentRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.CapabilityExecutionResult;
import com.lingxi.workbench.application.runtime.model.ChatExecutionContext;
import com.lingxi.workbench.application.runtime.model.ExecutionDecision;
import com.lingxi.workbench.application.runtime.model.ExecutionEventCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionPlan;
import com.lingxi.workbench.application.runtime.model.ExecutionPlanSnapshotCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionRunStartCommand;
import com.lingxi.workbench.application.runtime.model.ExecutionStepAttemptCommand;
import com.lingxi.workbench.application.runtime.model.FinalSynthesisResult;
import com.lingxi.workbench.application.runtime.model.InteractionSessionOpenCommand;
import com.lingxi.workbench.application.runtime.model.InteractionResolution;
import com.lingxi.workbench.application.runtime.model.PlanExecutionRequest;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlanExecutorRuntimeContext;
import com.lingxi.workbench.application.runtime.model.PlanStep;
import com.lingxi.workbench.application.runtime.model.PlanStepResult;
import com.lingxi.workbench.application.runtime.model.PlannerResult;
import com.lingxi.workbench.application.runtime.model.StepInputReference;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionPlanSnapshotEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionStepAttemptEntity;
import com.lingxi.workbench.application.runtime.service.execution.ChatExecutionEventPublisher;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.execution.ExecutionFailureClassifier;
import com.lingxi.workbench.application.runtime.service.interaction.InteractionSessionService;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.bo.ChatStepOutputEventBO;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.infra.ai.agent.common.enums.AgentType;
import com.lingxi.workbench.infra.ai.agent.model.PlanExecutorPolicy;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.FallbackAgent;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import org.junit.jupiter.api.Test;
import io.micrometer.observation.ObservationRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanExecutionServiceImplTest {

    @Test
    void shouldExecutePlanSeriallyAndSynthesizeCompletedResults() {
        ApplicationCapabilityDefinition collect = capability("collect", ApplicationCapabilityType.AGENT, CapabilityRiskLevel.READ_ONLY, 2);
        ApplicationCapabilityDefinition submit = capability("submit", ApplicationCapabilityType.AGENT, CapabilityRiskLevel.READ_ONLY, 2);
        List<String> executionOrder = new ArrayList<>();
        RecordingPersistence persistence = new RecordingPersistence();
        RecordingPublisher publisher = new RecordingPublisher();
        PlanExecutionServiceImpl service = new PlanExecutionServiceImpl(request -> {
            executionOrder.add(request.getCapability().getCode());
            request.getContext().getEventPublisher().publishText(new ChatStreamChunk("步骤增量"));
            if ("collect".equals(request.getCapability().getCode())) {
                assertEquals("整理资料", request.getInputs().getString("query"));
                assertEquals("整理资料", request.getContext().getRuntimeVariables()
                        .require(RuntimeVariableAccessors.USER_QUERY));
                return new CapabilityExecutionResult("{\"ticket\":\"T-1\"}", 11L, List.of());
            }
            assertTrue(request.getInputs().getString("query").contains("整理资料"));
            assertTrue(request.getInputs().getString("query").contains("{\"ticket\":\"T-1\"}"));
            assertEquals(request.getInputs().getString("query"), request.getContext().getRuntimeVariables()
                    .require(RuntimeVariableAccessors.USER_QUERY));
            return new CapabilityExecutionResult("已提交", 12L, List.of());
        }, request -> {
            throw new AssertionError("成功计划不应触发重规划");
        }, request -> {
            assertEquals(List.of("collect", "submit"), request.stepResults().stream().map(result -> result.getCapabilityCode()).toList());
            return new FinalSynthesisResult("任务完成", TokenUsage.of(3, 2, 5));
        }, persistence, interactionService(), new PlanInputCompiler(), new ExecutionFailureClassifier(),
                ObservationRegistry.NOOP);

        PlanExecutionResult result = service.execute(request(plan(step("collect", "collect", List.of()),
                step("submit", "submit", List.of("collect"))), List.of(collect, submit), Set.of(), publisher));

        assertEquals("任务完成", result.content());
        assertEquals(12L, result.modelInstanceId());
        assertEquals(List.of("collect", "submit"), executionOrder);
        assertEquals(2, persistence.attempts.size());
        assertEquals(ExecutionEventType.RUN_COMPLETED, persistence.events.getLast().getEventType());
        assertEquals(0, publisher.rootChunks.size());
        assertEquals(List.of("collect", "submit"),
                publisher.stepOutputs.stream().map(ChatStepOutputEventBO::stepId).toList());
        assertTrue(publisher.stepOutputs.stream().allMatch(output -> "run-1".equals(output.runId())));
    }

    @Test
    void shouldKeepDirectCapabilityTextInAssistantAnswerStream() {
        ApplicationCapabilityDefinition collect = capability(
                "collect", ApplicationCapabilityType.AGENT, CapabilityRiskLevel.READ_ONLY, 1);
        RecordingPublisher publisher = new RecordingPublisher();
        PlanExecutionServiceImpl service = new PlanExecutionServiceImpl(request -> {
            request.getContext().getEventPublisher().publishText(new ChatStreamChunk("直接回答"));
            return new CapabilityExecutionResult("直接回答", 11L, List.of());
        }, request -> {
            throw new AssertionError("直接能力不应触发重规划");
        }, request -> {
            throw new AssertionError("直接能力不应进入最终综合");
        }, new RecordingPersistence(), interactionService(), new PlanInputCompiler(),
                new ExecutionFailureClassifier(), ObservationRegistry.NOOP);
        ExecutionDecision decision = ExecutionDecision.builder()
                .action(ExecutionDecisionAction.EXECUTE_REACT)
                .capabilityCode("collect")
                .build();

        PlanExecutionResult result = service.execute(request(
                decision, List.of(collect), Set.of(), publisher));

        assertEquals("直接回答", result.content());
        assertEquals(List.of("直接回答"), publisher.rootChunks);
        assertEquals(0, publisher.stepOutputs.size());
    }

    @Test
    void shouldFinalizeCompletedResultsWhenReplanLimitIsReached() {
        ApplicationCapabilityDefinition write = capability("write", ApplicationCapabilityType.TOOL, CapabilityRiskLevel.WRITE, 3);
        ApplicationCapabilityDefinition unstable = capability("unstable", ApplicationCapabilityType.AGENT, CapabilityRiskLevel.READ_ONLY, 3);
        AtomicInteger writeCalls = new AtomicInteger();
        AtomicInteger replans = new AtomicInteger();
        RecordingPersistence persistence = new RecordingPersistence();
        PlanExecutionServiceImpl service = new PlanExecutionServiceImpl(request -> {
            if ("write".equals(request.getCapability().getCode())) {
                writeCalls.incrementAndGet();
                return new CapabilityExecutionResult("写入完成", null, List.of());
            }
            throw new IllegalStateException("下游暂不可用");
        }, request -> {
            replans.incrementAndGet();
            return new PlannerResult(
                    plan(step("unstable-again", "unstable", List.of(), userQuery())), TokenUsage.of(2, 1, 3), 1);
        }, request -> {
            assertEquals(List.of("write"), request.stepResults().stream()
                    .map(PlanStepResult::getCapabilityCode).toList());
            assertTrue(request.isLimited());
            return new FinalSynthesisResult("已基于写入结果完成收口", TokenUsage.of(2, 1, 3));
        }, persistence, interactionService(), new PlanInputCompiler(), new ExecutionFailureClassifier(),
                ObservationRegistry.NOOP);

        PlanExecutionResult result = service.execute(request(
                plan(step("write-once", "write", List.of(), userQuery()), step("unstable", "unstable", List.of("write-once"))),
                List.of(write, unstable), Set.of("write"), ChatExecutionEventPublisher.noop()));

        assertEquals("已基于写入结果完成收口", result.content());
        assertEquals(1, writeCalls.get());
        assertEquals(1, replans.get());
        assertEquals(2, persistence.plans.size());
        assertEquals(ExecutionEventType.RUN_COMPLETED, persistence.events.getLast().getEventType());
        assertTrue(persistence.events.stream().anyMatch(event -> event.getEventType() == ExecutionEventType.FINALIZING));
        assertTrue(persistence.events.stream()
                .filter(event -> event.getEventType() == ExecutionEventType.STEP_FAILED)
                .anyMatch(event -> "能力执行失败".equals(event.getMessage())));
    }

    @Test
    void shouldClarifyWhenHardTokenBoundaryIsReachedWithoutCompletedResult() {
        ApplicationCapabilityDefinition unstable = capability(
                "unstable", ApplicationCapabilityType.AGENT, CapabilityRiskLevel.READ_ONLY, 2);
        AtomicReference<InteractionSessionOpenCommand> openedSession = new AtomicReference<>();
        InteractionSessionService interactionService = new InteractionSessionService() {
            @Override
            public ChatClarificationEventBO open(InteractionSessionOpenCommand command) {
                openedSession.set(command);
                return ChatClarificationEventBO.builder()
                        .clarificationId("clarification-1")
                        .interactionType(command.getInteractionType().name())
                        .question(command.getContext().getClarification().getQuestion())
                        .options(List.of())
                        .round(1)
                        .build();
            }

            @Override
            public Optional<InteractionResolution> resolve(AppRuntimeSnapshot application, ChatTurnBO turn,
                                                                  Long userId, String query,
                                                                  ClarificationResponseRequest response) {
                return Optional.empty();
            }
        };
        RecordingPersistence persistence = new RecordingPersistence();
        PlanExecutionServiceImpl service = new PlanExecutionServiceImpl(request -> {
            throw new IllegalStateException("下游暂不可用");
        }, request -> {
            throw new AssertionError("预算边界命中后不得继续重规划");
        }, request -> {
            throw new AssertionError("没有完整步骤结果时不得调用最终综合模型");
        }, persistence, interactionService, new PlanInputCompiler(), new ExecutionFailureClassifier(),
                ObservationRegistry.NOOP);
        PlanExecutionRequest request = request(
                plan(step("unstable", "unstable", List.of())), List.of(unstable), Set.of(),
                ChatExecutionEventPublisher.noop());
        request.getExecutionContext().recordUsage(TokenUsage.of(7_000, 7_000, 14_000));

        PlanExecutionResult result = service.execute(request);

        assertTrue(result.content().contains("请补充"));
        assertEquals("run-1", openedSession.get().getRunId());
        assertEquals(ExecutionEventType.CLARIFICATION_REQUIRED, persistence.events.getLast().getEventType());
        assertTrue(persistence.events.stream().anyMatch(event -> event.getEventType() == ExecutionEventType.FINALIZING));
    }

    @Test
    void shouldReserveTokenGraceForFinalizationBeforeFirstPlanStep() {
        ApplicationCapabilityDefinition collect = capability(
                "collect", ApplicationCapabilityType.AGENT, CapabilityRiskLevel.READ_ONLY, 2);
        AtomicReference<InteractionSessionOpenCommand> openedSession = new AtomicReference<>();
        InteractionSessionService interactionService = capturingInteractionService(openedSession);
        RecordingPersistence persistence = new RecordingPersistence();
        PlanExecutionServiceImpl service = new PlanExecutionServiceImpl(request -> {
            throw new AssertionError("普通工作Token用尽后不得再执行首个计划步骤");
        }, request -> {
            throw new AssertionError("普通工作Token用尽后不得继续重规划");
        }, request -> {
            throw new AssertionError("没有完整步骤结果时不得调用最终综合模型");
        }, persistence, interactionService, new PlanInputCompiler(), new ExecutionFailureClassifier(),
                ObservationRegistry.NOOP);
        PlanExecutionRequest request = request(
                plan(step("collect", "collect", List.of())), List.of(collect), Set.of(),
                ChatExecutionEventPublisher.noop());
        request.getExecutionContext().recordUsage(TokenUsage.of(8_000, 4_000, 12_000));

        PlanExecutionResult result = service.execute(request);

        assertTrue(result.content().contains("请补充"));
        assertEquals("run-1", openedSession.get().getRunId());
        assertTrue(persistence.events.stream().anyMatch(event -> event.getEventType() == ExecutionEventType.PLAN_CREATED));
        assertTrue(persistence.events.stream().anyMatch(event -> event.getEventType() == ExecutionEventType.FINALIZING));
        assertEquals(ExecutionEventType.CLARIFICATION_REQUIRED, persistence.events.getLast().getEventType());
    }

    @Test
    void shouldPersistCancelledRunBeforeAnyStepStarts() {
        ApplicationCapabilityDefinition capability = capability("collect", ApplicationCapabilityType.AGENT, CapabilityRiskLevel.READ_ONLY, 1);
        RecordingPersistence persistence = new RecordingPersistence();
        ChatExecutionEventPublisher cancelledPublisher = new ChatExecutionEventPublisher() {
            @Override
            public void publishText(com.lingxi.workbench.infra.ai.chat.model.bo.ChatStreamChunk chunk) {
            }

            @Override
            public void publishTool(AgentToolExecutionEvent event) {
            }

            @Override
            public boolean isCancelled() {
                return true;
            }
        };
        PlanExecutionServiceImpl service = new PlanExecutionServiceImpl(request -> {
            throw new AssertionError("取消后不得执行能力");
        }, request -> {
            throw new AssertionError("取消后不得重规划");
        }, request -> {
            throw new AssertionError("取消后不得最终综合");
        }, persistence, interactionService(), new PlanInputCompiler(), new ExecutionFailureClassifier(),
                ObservationRegistry.NOOP);

        assertThrows(CancellationException.class, () -> service.execute(request(
                plan(step("collect", "collect", List.of())), List.of(capability), Set.of(), cancelledPublisher)));

        assertEquals(0, persistence.plans.size());
        assertEquals(0, persistence.attempts.size());
        assertEquals(ExecutionEventType.RUN_CANCELLED, persistence.events.getLast().getEventType());
    }

    private static PlanExecutionRequest request(ExecutionDecision decision, List<ApplicationCapabilityDefinition> capabilities, Set<String> confirmed, ChatExecutionEventPublisher publisher) {
        ConversationContext conversation = conversationContext(capabilities);
        ChatExecutionContext execution = ChatExecutionContext.builder()
                .executionId("execution-1")
                .runtimeVariables(conversation.getRuntimeVariables())
                .turn(conversation.getTurn())
                .eventPublisher(publisher)
                .conversationContext(conversation)
                .runtimePolicy(conversation.getApplicationSnapshot().definition().runtimePolicy())
                .build();
        return PlanExecutionRequest.builder()
                .runId("run-1")
                .initialDecision(decision)
                .conversationContext(conversation)
                .executionContext(execution)
                .confirmedCapabilityCodes(confirmed)
                .planExecutor(planExecutor(conversation))
                .build();
    }

    private static ConversationContext conversationContext(List<ApplicationCapabilityDefinition> capabilities) {
        LoginUser user = new LoginUser(7L, "tester", "测试用户", null);
        ChatTurnBO turn = new ChatTurnBO(10L, 11L, 12L, 1);
        RuntimeVariables variables = new RuntimeVariablesInitializer("Lingxi Workbench").initialize(user, "整理资料", turn.conversationId());
        variables.put(RuntimeVariableAccessors.SESSION_HISTORY, List.of());
        ApplicationCapabilityDefinition planExecutor = capability(
                "plan_executor", ApplicationCapabilityType.AGENT, CapabilityRiskLevel.READ_ONLY, 1);
        List<ApplicationCapabilityDefinition> catalog = new ArrayList<>();
        catalog.add(planExecutor);
        catalog.addAll(capabilities);
        ApplicationDefinition definition = ApplicationDefinition.builder()
                .schemaVersion(com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION)
                .fallback(new FallbackAgent("plan_executor"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(catalog).build())
                .runtimePolicy(com.lingxi.workbench.workflow.model.ApplicationRuntimePolicy.builder()
                        .maxModelCalls(6)
                        .maxToolCalls(4)
                        .maxDelegations(0)
                        .totalTokenBudget(12000)
                        .timeoutSeconds(60)
                        .build())
                .build();
        return ConversationContext.builder()
                .applicationSnapshot(new AppRuntimeSnapshot(1L, "test-app", 2L, definition))
                .loginUser(user)
                .turn(turn)
                .originalQuery("整理资料")
                .rewrittenQuery("整理资料")
                .recentMessages(List.of())
                .budget(ContextBudgetPolicy.builder().maxHistoryRounds(5).maxSystemPromptTokens(2000).maxCapabilityTokens(2000)
                        .maxHistoryTokens(2000).maxMemoryTokens(2000).maxInputTokens(2000).maxObservationTokens(2000)
                        .reservedOutputTokens(1000).build())
                .runtimeVariables(variables)
                .build();
    }

    private static PlanExecutorRuntimeContext planExecutor(ConversationContext conversation) {
        ApplicationCapabilityDefinition capability = conversation.getApplicationSnapshot().definition()
                .capabilityCatalog().capabilities().stream()
                .filter(item -> "plan_executor".equals(item.getCode()))
                .findFirst()
                .orElseThrow();
        AgentRuntimeDefinition definition = AgentRuntimeDefinition.builder()
                .schemaVersion(com.lingxi.workbench.infra.ai.agent.common.constants.AgentSchemaConstants.AGENT_SCHEMA_VERSION)
                .agentType(AgentType.PLAN_EXECUTOR)
                .chatModelInstanceId(21L)
                .systemPromptVersionId(22L)
                .toolCodes(List.of())
                .planExecutorPolicy(PlanExecutorPolicy.builder()
                        .maxSteps(6)
                        .maxReplans(1)
                        .maxProtocolRepairAttempts(1)
                        .build())
                .build();
        return PlanExecutorRuntimeContext.builder()
                .capability(capability)
                .agent(new AgentRuntimeSnapshot(1L, 2L, definition))
                .build();
    }

    private static ApplicationCapabilityDefinition capability(String code, ApplicationCapabilityType type, CapabilityRiskLevel riskLevel, int maxCalls) {
        return ApplicationCapabilityDefinition.builder()
                .code(code)
                .name(code)
                .type(type)
                .targetId(1L)
                .targetVersionId(type == ApplicationCapabilityType.TOOL ? null : 2L)
                .inputSchema(new JSONObject())
                .outputSchema(new JSONObject())
                .riskLevel(riskLevel)
                .timeoutSeconds(10)
                .maxCalls(maxCalls)
                .resultTokenBudget(1000)
                .build();
    }

    private static ExecutionDecision plan(PlanStep... steps) {
        return ExecutionDecision.builder()
                .action(ExecutionDecisionAction.EXECUTE_PLAN)
                .plan(ExecutionPlan.builder().title("测试计划").steps(List.of(steps)).build())
                .build();
    }

    private static PlanStep step(String id, String capabilityCode, List<String> dependencies, StepInputReference input) {
        return PlanStep.builder().id(id).name(id).capabilityCode(capabilityCode)
                .dependsOn(dependencies).inputs(List.of(input)).build();
    }

    private static PlanStep step(String id, String capabilityCode, List<String> dependencies) {
        return PlanStep.builder().id(id).name(id).capabilityCode(capabilityCode)
                .dependsOn(dependencies).inputs(List.of()).build();
    }

    private static StepInputReference userQuery() {
        return StepInputReference.builder().name("query").source(StepInputSource.USER_QUERY).build();
    }

    private static StepInputReference stepOutput(String stepId, String field) {
        return StepInputReference.builder().name(field).source(StepInputSource.STEP_OUTPUT).stepId(stepId).value(field).build();
    }

    private static InteractionSessionService interactionService() {
        return new InteractionSessionService() {
            @Override
            public ChatClarificationEventBO open(InteractionSessionOpenCommand command) {
                throw new AssertionError("测试计划不应发起澄清");
            }

            @Override
            public Optional<InteractionResolution> resolve(AppRuntimeSnapshot application, ChatTurnBO turn, Long userId, String query, ClarificationResponseRequest response) {
                return Optional.empty();
            }
        };
    }

    private static InteractionSessionService capturingInteractionService(
            AtomicReference<InteractionSessionOpenCommand> openedSession) {
        return new InteractionSessionService() {
            @Override
            public ChatClarificationEventBO open(InteractionSessionOpenCommand command) {
                openedSession.set(command);
                return ChatClarificationEventBO.builder()
                        .clarificationId("clarification-1")
                        .interactionType(command.getInteractionType().name())
                        .question(command.getContext().getClarification().getQuestion())
                        .options(List.of())
                        .round(1)
                        .build();
            }

            @Override
            public Optional<InteractionResolution> resolve(AppRuntimeSnapshot application, ChatTurnBO turn,
                                                                  Long userId, String query,
                                                                  ClarificationResponseRequest response) {
                return Optional.empty();
            }
        };
    }

    private static final class RecordingPersistence implements ExecutionPersistenceService {
        private final List<ExecutionPlanSnapshotCommand> plans = new ArrayList<>();
        private final List<ExecutionStepAttemptCommand> attempts = new ArrayList<>();
        private final List<ExecutionEventCommand> events = new ArrayList<>();

        @Override
        public void startRun(ExecutionRunStartCommand command) {
        }

        @Override
        public void appendPlan(ExecutionPlanSnapshotCommand command) {
            plans.add(command);
        }

        @Override
        public void appendStepAttempt(ExecutionStepAttemptCommand command) {
            attempts.add(command);
        }

        @Override
        public ExecutionEventEntity appendEvent(ExecutionEventCommand command) {
            events.add(command);
            ExecutionEventEntity entity = new ExecutionEventEntity();
            entity.setEventType(command.getEventType());
            return entity;
        }

        @Override
        public List<ExecutionPlanSnapshotEntity> findPlans(String runId) {
            return List.of();
        }

        @Override
        public List<ExecutionStepAttemptEntity> findStepAttempts(String runId) {
            return List.of();
        }

        @Override
        public List<ExecutionEventEntity> findEventsAfter(String runId, long afterSequence) {
            return List.of();
        }
    }

    private static final class RecordingPublisher implements ChatExecutionEventPublisher {
        private final List<String> rootChunks = new ArrayList<>();
        private final List<ChatStepOutputEventBO> stepOutputs = new ArrayList<>();

        @Override
        public void publishText(ChatStreamChunk chunk) {
            rootChunks.add(chunk.content());
        }

        @Override
        public void publishTool(AgentToolExecutionEvent event) {
        }

        @Override
        public void publishStepOutput(ChatStepOutputEventBO event) {
            stepOutputs.add(event);
        }
    }
}
