package com.lingxi.workbench.application.runtime.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionDecisionAction;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionEventType;
import com.lingxi.workbench.application.runtime.common.enums.StepInputSource;
import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
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
import com.lingxi.workbench.application.runtime.model.PlanExecutionRequest;
import com.lingxi.workbench.application.runtime.model.PlanExecutionResult;
import com.lingxi.workbench.application.runtime.model.PlanStep;
import com.lingxi.workbench.application.runtime.model.PlannerInteractionResolution;
import com.lingxi.workbench.application.runtime.model.PlannerResult;
import com.lingxi.workbench.application.runtime.model.StepInputReference;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionEventEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionPlanSnapshotEntity;
import com.lingxi.workbench.application.runtime.model.entity.ExecutionStepAttemptEntity;
import com.lingxi.workbench.application.runtime.service.ChatExecutionEventPublisher;
import com.lingxi.workbench.application.runtime.service.ExecutionPersistenceService;
import com.lingxi.workbench.application.runtime.service.InteractionSessionService;
import com.lingxi.workbench.chat.model.bo.ChatClarificationEventBO;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ContextBudget;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.model.request.ClarificationResponseRequest;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.tool.model.AgentToolExecutionEvent;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.workflow.model.PlannerPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlanExecutionServiceImplTest {

    @Test
    void shouldExecutePlanSeriallyAndSynthesizeCompletedResults() {
        ApplicationCapabilityDefinition collect = capability("collect", ApplicationCapabilityType.REACT_AGENT, CapabilityRiskLevel.READ_ONLY, 2);
        ApplicationCapabilityDefinition submit = capability("submit", ApplicationCapabilityType.REACT_AGENT, CapabilityRiskLevel.READ_ONLY, 2);
        List<String> executionOrder = new ArrayList<>();
        RecordingPersistence persistence = new RecordingPersistence();
        PlanExecutionServiceImpl service = new PlanExecutionServiceImpl(request -> {
            executionOrder.add(request.capability().getCode());
            if ("collect".equals(request.capability().getCode())) {
                assertEquals("整理资料", request.inputs().getString("query"));
                return new CapabilityExecutionResult("{\"ticket\":\"T-1\"}", 11L, List.of());
            }
            assertEquals("T-1", request.inputs().getString("ticket"));
            return new CapabilityExecutionResult("已提交", 12L, List.of());
        }, request -> {
            throw new AssertionError("成功计划不应触发重规划");
        }, request -> {
            assertEquals(List.of("collect", "submit"), request.stepResults().stream().map(result -> result.getCapabilityCode()).toList());
            return new FinalSynthesisResult("任务完成", TokenUsage.of(3, 2, 5));
        }, persistence, interactionService());

        PlanExecutionResult result = service.execute(request(plan(step("collect", "collect", List.of(), userQuery()),
                step("submit", "submit", List.of("collect"), stepOutput("collect", "ticket"))), List.of(collect, submit), Set.of(), ChatExecutionEventPublisher.noop()));

        assertEquals("任务完成", result.content());
        assertEquals(12L, result.modelInstanceId());
        assertEquals(List.of("collect", "submit"), executionOrder);
        assertEquals(2, persistence.attempts.size());
        assertEquals(ExecutionEventType.RUN_COMPLETED, persistence.events.getLast().getEventType());
    }

    @Test
    void shouldBoundReplanAndNeverRepeatCompletedWriteCapability() {
        ApplicationCapabilityDefinition write = capability("write", ApplicationCapabilityType.TOOL, CapabilityRiskLevel.WRITE, 3);
        ApplicationCapabilityDefinition unstable = capability("unstable", ApplicationCapabilityType.REACT_AGENT, CapabilityRiskLevel.READ_ONLY, 3);
        AtomicInteger writeCalls = new AtomicInteger();
        AtomicInteger replans = new AtomicInteger();
        RecordingPersistence persistence = new RecordingPersistence();
        PlanExecutionServiceImpl service = new PlanExecutionServiceImpl(request -> {
            if ("write".equals(request.capability().getCode())) {
                writeCalls.incrementAndGet();
                return new CapabilityExecutionResult("写入完成", null, List.of());
            }
            throw new IllegalStateException("下游暂不可用");
        }, request -> {
            replans.incrementAndGet();
            return new PlannerResult(plan(step("write-again", "write", List.of(), userQuery())), TokenUsage.of(2, 1, 3));
        }, request -> {
            throw new AssertionError("失败计划不应进入最终综合");
        }, persistence, interactionService());

        assertThrows(RuntimeException.class, () -> service.execute(request(
                plan(step("write-once", "write", List.of(), userQuery()), step("unstable", "unstable", List.of("write-once"), userQuery())),
                List.of(write, unstable), Set.of("write"), ChatExecutionEventPublisher.noop())));

        assertEquals(1, writeCalls.get());
        assertEquals(1, replans.get());
        assertEquals(2, persistence.plans.size());
        assertEquals(ExecutionEventType.RUN_FAILED, persistence.events.getLast().getEventType());
    }

    @Test
    void shouldPersistCancelledRunBeforeAnyStepStarts() {
        ApplicationCapabilityDefinition capability = capability("collect", ApplicationCapabilityType.REACT_AGENT, CapabilityRiskLevel.READ_ONLY, 1);
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
        }, persistence, interactionService());

        assertThrows(CancellationException.class, () -> service.execute(request(
                plan(step("collect", "collect", List.of(), userQuery())), List.of(capability), Set.of(), cancelledPublisher)));

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
                .build();
        return PlanExecutionRequest.builder()
                .runId("run-1")
                .initialDecision(decision)
                .conversationContext(conversation)
                .executionContext(execution)
                .confirmedCapabilityCodes(confirmed)
                .startedAt(Instant.now())
                .build();
    }

    private static ConversationContext conversationContext(List<ApplicationCapabilityDefinition> capabilities) {
        LoginUser user = new LoginUser(7L, "tester", "测试用户", null);
        ChatTurnBO turn = new ChatTurnBO(10L, 11L, 12L, 1);
        RuntimeVariables variables = new RuntimeVariablesInitializer("Lingxi Workbench").initialize(user, "整理资料", turn.conversationId());
        variables.put(RuntimeVariableAccessors.SESSION_HISTORY, List.of());
        ApplicationCapabilityDefinition defaultAgent = capability("default_agent", ApplicationCapabilityType.REACT_AGENT, CapabilityRiskLevel.READ_ONLY, 1);
        List<ApplicationCapabilityDefinition> catalog = new ArrayList<>();
        catalog.add(defaultAgent);
        catalog.addAll(capabilities);
        PlannerPolicy policy = PlannerPolicy.builder().enabled(true).chatModelInstanceId(21L).promptVersionId(22L)
                .maxSteps(6).maxReplans(1).maxModelCalls(6).maxToolCalls(4).tokenBudget(12000).timeoutSeconds(60).build();
        ApplicationDefinition definition = ApplicationDefinition.builder()
                .schemaVersion(4)
                .defaultAgent(new ApplicationAgentReference(1L, 2L, "默认助手", "处理普通任务"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(catalog).build())
                .plannerPolicy(policy)
                .build();
        return ConversationContext.builder()
                .applicationSnapshot(new ApplicationRuntimeSnapshot(1L, "test-app", 2L, definition))
                .loginUser(user)
                .turn(turn)
                .originalQuery("整理资料")
                .contextualizedQuery("整理资料")
                .recentMessages(List.of())
                .budget(ContextBudget.builder().maxHistoryRounds(5).maxSystemPromptTokens(2000).maxCapabilityTokens(2000)
                        .maxHistoryTokens(2000).maxMemoryTokens(2000).maxInputTokens(2000).maxObservationTokens(2000)
                        .reservedOutputTokens(1000).build())
                .runtimeVariables(variables)
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
            public Optional<PlannerInteractionResolution> resolve(ApplicationRuntimeSnapshot application, ChatTurnBO turn, Long userId, String query, ClarificationResponseRequest response) {
                return Optional.empty();
            }
        };
    }

    private static final class RecordingPersistence implements ExecutionPersistenceService {
        private final List<ExecutionPlanSnapshotCommand> plans = new ArrayList<>();
        private final List<ExecutionStepAttemptCommand> attempts = new ArrayList<>();
        private final List<ExecutionEventCommand> events = new ArrayList<>();

        @Override
        public String startRun(ExecutionRunStartCommand command) {
            return "run-1";
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
}
