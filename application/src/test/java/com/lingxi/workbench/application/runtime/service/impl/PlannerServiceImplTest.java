package com.lingxi.workbench.application.runtime.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.common.enums.ExecutionDecisionAction;
import com.lingxi.workbench.application.runtime.common.enums.InteractionSessionType;
import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.PlannerRequest;
import com.lingxi.workbench.application.runtime.model.PlannerResult;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.PromptRuntimeService;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.workflow.model.ContextBudgetPolicy;
import com.lingxi.workbench.chat.model.ccontext.ConversationContext;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariableAccessors;
import com.lingxi.workbench.chat.model.ccontext.RuntimeVariables;
import com.lingxi.workbench.chat.service.impl.PromptTemplateRenderer;
import com.lingxi.workbench.framework.context.LoginUser;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.infra.ai.chat.ChatCompletionGateway;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatCompletionResult;
import com.lingxi.workbench.infra.ai.chat.model.bo.ChatModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.chat.model.bo.TokenUsage;
import com.lingxi.workbench.infra.ai.chat.model.command.ChatCompletionCommand;
import com.lingxi.workbench.infra.ai.embedding.model.EmbeddingModelRuntimeConfig;
import com.lingxi.workbench.infra.ai.prompt.common.enums.PromptRole;
import com.lingxi.workbench.model.service.ModelRuntimeConfigService;
import com.lingxi.workbench.workflow.model.ApplicationAgentReference;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityDefinition;
import com.lingxi.workbench.workflow.model.ApplicationCapabilityType;
import com.lingxi.workbench.workflow.model.ApplicationDefinition;
import com.lingxi.workbench.workflow.model.CapabilityCatalogSnapshot;
import com.lingxi.workbench.workflow.model.CapabilityRiskLevel;
import com.lingxi.workbench.workflow.model.PlannerPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerServiceImplTest {

    @Test
    void shouldExposeOnlySafeCapabilityDescriptorsAndClampModelPolicy() {
        List<ChatCompletionCommand> captured = new ArrayList<>();
        PlannerServiceImpl service = service(captured, List.of(completion(validDecision(), TokenUsage.of(10, 5, 15))));

        PlannerResult result = service.decide(request(2000));

        ChatCompletionCommand command = captured.getFirst();
        assertEquals("support", result.decision().getCapabilityCode());
        assertEquals(1, result.modelCalls());
        assertTrue(command.systemPrompt().contains("support"));
        assertFalse(command.systemPrompt().contains("987654321"));
        assertFalse(command.systemPrompt().contains("876543210"));
        assertFalse(command.systemPrompt().contains("configuration-secret"));
        assertEquals(60, command.modelConfig().getTimeoutSeconds());
        assertTrue(command.modelConfig().getMaxTokens() <= 500);
    }

    @Test
    void shouldRepairInvalidDecisionOnceAndAccumulateUsage() {
        List<ChatCompletionCommand> captured = new ArrayList<>();
        PlannerServiceImpl service = service(captured, List.of(
                completion(invalidClarification(), TokenUsage.of(10, 5, 15)),
                completion(validDecision(), TokenUsage.of(12, 6, 18))));

        PlannerResult result = service.decide(request(2000));

        assertEquals(ExecutionDecisionAction.EXECUTE_REACT, result.decision().getAction());
        assertEquals(2, result.modelCalls());
        assertEquals(TokenUsage.of(22, 11, 33), result.tokenUsage());
        assertEquals(2, captured.size());
        assertTrue(captured.getLast().prompt().contains("上一次JSON决策未通过平台协议或业务校验"));
    }

    @Test
    void shouldReturnSafeClarificationAfterBoundedRepairFails() {
        PlannerServiceImpl service = service(new ArrayList<>(), List.of(
                completion(invalidClarification(), TokenUsage.of(10, 5, 15)),
                completion(invalidClarification(), TokenUsage.of(10, 5, 15))));

        PlannerResult result = service.decide(request(2000));

        assertEquals(ExecutionDecisionAction.CLARIFY, result.decision().getAction());
        assertEquals(InteractionSessionType.PLANNER_CLARIFICATION,
                result.decision().getClarification().getInteractionType());
        assertTrue(result.decision().getClarification().capabilityCodes().isEmpty());
        assertEquals(2, result.modelCalls());
    }

    @Test
    void shouldNotRepairWhenOnlyOneModelCallRemains() {
        List<ChatCompletionCommand> captured = new ArrayList<>();
        PlannerServiceImpl service = service(captured, List.of(
                completion(invalidClarification(), TokenUsage.of(10, 5, 15))));

        PlannerResult result = service.decide(request(2000, 1));

        assertEquals(ExecutionDecisionAction.CLARIFY, result.decision().getAction());
        assertEquals(1, result.modelCalls());
        assertEquals(1, captured.size());
    }

    @Test
    void shouldRejectActualPlannerUsageAbovePublishedBudget() {
        PlannerServiceImpl service = service(new ArrayList<>(), List.of(
                completion(validDecision(), TokenUsage.of(1500, 700, 2200))));

        assertThrows(BusinessException.class, () -> service.decide(request(2000)));
    }

    private static PlannerServiceImpl service(List<ChatCompletionCommand> captured,
                                              List<ChatCompletionResult> completions) {
        AtomicInteger responseIndex = new AtomicInteger();
        ChatCompletionGateway gateway = new ChatCompletionGateway() {
            @Override
            public Mono<ChatCompletionResult> complete(ChatCompletionCommand command) {
                throw new AssertionError("Planner必须使用JSON Object模型调用");
            }

            @Override
            public Mono<ChatCompletionResult> completeJson(ChatCompletionCommand command) {
                captured.add(command);
                return Mono.just(completions.get(responseIndex.getAndIncrement()));
            }
        };
        PromptRuntimeService promptService = promptVersionId ->
                new PromptRuntimeSnapshot(promptVersionId, "你负责规划任务。", List.of(), PromptRole.SYSTEM);
        TokenCountEstimator estimator = new DeterministicTokenEstimator();
        return new PlannerServiceImpl(gateway, modelService(), promptService, new PromptTemplateRenderer(), estimator,
                new ExecutionDecisionParser(), new ExecutionDecisionValidator());
    }

    private static PlannerRequest request(int plannerTokenBudget) {
        return request(plannerTokenBudget, 6);
    }

    private static PlannerRequest request(int plannerTokenBudget, int availableModelCalls) {
        return PlannerRequest.builder()
                .conversationContext(context(plannerTokenBudget))
                .planNo(1)
                .availableModelCalls(availableModelCalls)
                .build();
    }

    private static ChatCompletionResult completion(String content, TokenUsage usage) {
        return new ChatCompletionResult(content, usage);
    }

    private static String validDecision() {
        return "{\"action\":\"EXECUTE_REACT\",\"capabilityCode\":\"support\"}";
    }

    private static String invalidClarification() {
        return """
                {"action":"CLARIFY","clarification":{"question":"请选择任务","options":[],
                "interactionType":"PLANNER_CLARIFICATION","capabilityCodes":["support"]}}
                """;
    }

    private static ModelRuntimeConfigService modelService() {
        return new ModelRuntimeConfigService() {
            @Override
            public ChatModelRuntimeConfig getDefaultChatModel() {
                return getChatModelConfig(21L);
            }

            @Override
            public ChatModelRuntimeConfig getChatModelConfig(Long instanceId) {
                return ChatModelRuntimeConfig.builder().instanceId(instanceId).modelCode("planner-model")
                        .maxTokens(500).timeoutSeconds(120).maxRetries(0).build();
            }

            @Override
            public EmbeddingModelRuntimeConfig getEmbeddingModelConfig(Long instanceId) {
                throw new AssertionError("Planner不应加载Embedding模型");
            }
        };
    }

    private static ConversationContext context(int plannerTokenBudget) {
        LoginUser user = new LoginUser(7L, "tester", "测试用户", null);
        ChatTurnBO turn = new ChatTurnBO(10L, 11L, 12L, 1);
        RuntimeVariables variables = new RuntimeVariablesInitializer("Lingxi Workbench").initialize(user, "处理售后问题", turn.conversationId());
        variables.put(RuntimeVariableAccessors.SESSION_HISTORY, List.of());
        ApplicationCapabilityDefinition defaultAgent = capability("default_agent", 1L, 2L);
        ApplicationCapabilityDefinition support = capability("support", 987654321L, 876543210L);
        List<ApplicationCapabilityDefinition> capabilities = new ArrayList<>();
        capabilities.add(defaultAgent);
        capabilities.add(support);
        PlannerPolicy planner = PlannerPolicy.builder().enabled(true).chatModelInstanceId(21L).promptVersionId(22L)
                .maxSteps(6).maxReplans(1).maxModelCalls(6).maxToolCalls(4)
                .tokenBudget(plannerTokenBudget).timeoutSeconds(60).build();
        ApplicationDefinition definition = ApplicationDefinition.builder()
                .schemaVersion(com.lingxi.workbench.workflow.common.constants.ApplicationCollaborationConstants.APPLICATION_SCHEMA_VERSION)
                .defaultAgent(new ApplicationAgentReference(1L, 2L, "默认助手", "处理普通请求"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(capabilities).build())
                .plannerPolicy(planner)
                .build();
        return ConversationContext.builder()
                .applicationSnapshot(new AppRuntimeSnapshot(1L, "planner-app", 2L, definition))
                .loginUser(user)
                .turn(turn)
                .originalQuery("处理售后问题")
                .contextualizedQuery("处理售后问题")
                .recentMessages(List.of())
                .budget(ContextBudgetPolicy.builder().maxHistoryRounds(5).maxSystemPromptTokens(4000).maxCapabilityTokens(4000)
                        .maxHistoryTokens(4000).maxMemoryTokens(4000).maxInputTokens(4000).maxObservationTokens(4000)
                        .reservedOutputTokens(500).build())
                .runtimeVariables(variables)
                .build();
    }

    private static ApplicationCapabilityDefinition capability(String code, Long targetId, Long targetVersionId) {
        return ApplicationCapabilityDefinition.builder()
                .code(code)
                .name(code)
                .description("安全能力描述")
                .type(ApplicationCapabilityType.REACT_AGENT)
                .targetId(targetId)
                .targetVersionId(targetVersionId)
                .inputSchema(JSONObject.of("type", "object"))
                .outputSchema(JSONObject.of("type", "object"))
                .implementationType("INTERNAL")
                .configurationChecksum("configuration-secret")
                .riskLevel(CapabilityRiskLevel.READ_ONLY)
                .timeoutSeconds(10)
                .maxCalls(2)
                .resultTokenBudget(1000)
                .build();
    }

    private static final class DeterministicTokenEstimator implements TokenCountEstimator {

        @Override
        public int estimate(String text) {
            return text == null ? 0 : Math.max(1, text.length() / 4);
        }

        @Override
        public int estimate(MediaContent content) {
            return 0;
        }

        @Override
        public int estimate(Iterable<MediaContent> contents) {
            return 0;
        }
    }
}
