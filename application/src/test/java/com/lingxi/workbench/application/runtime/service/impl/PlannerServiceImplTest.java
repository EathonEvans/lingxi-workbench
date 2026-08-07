package com.lingxi.workbench.application.runtime.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.lingxi.workbench.application.runtime.model.ApplicationRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.PlannerRequest;
import com.lingxi.workbench.application.runtime.model.PlannerResult;
import com.lingxi.workbench.application.runtime.model.PromptRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.service.PromptRuntimeService;
import com.lingxi.workbench.chat.model.bo.ChatTurnBO;
import com.lingxi.workbench.chat.model.ccontext.ContextBudget;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerServiceImplTest {

    @Test
    void shouldExposeOnlySafeCapabilityDescriptorsAndClampModelPolicy() {
        AtomicReference<ChatCompletionCommand> captured = new AtomicReference<>();
        PlannerServiceImpl service = service(captured, TokenUsage.of(10, 5, 15));

        PlannerResult result = service.decide(PlannerRequest.builder().conversationContext(context(2000)).planNo(1).build());

        ChatCompletionCommand command = captured.get();
        assertEquals("support", result.decision().getCapabilityCode());
        assertTrue(command.systemPrompt().contains("support"));
        assertFalse(command.systemPrompt().contains("987654321"));
        assertFalse(command.systemPrompt().contains("876543210"));
        assertFalse(command.systemPrompt().contains("configuration-secret"));
        assertEquals(60, command.modelConfig().getTimeoutSeconds());
        assertTrue(command.modelConfig().getMaxTokens() <= 500);
    }

    @Test
    void shouldRejectActualPlannerUsageAbovePublishedBudget() {
        PlannerServiceImpl service = service(new AtomicReference<>(), TokenUsage.of(1500, 700, 2200));

        assertThrows(BusinessException.class, () -> service.decide(
                PlannerRequest.builder().conversationContext(context(2000)).planNo(1).build()));
    }

    private static PlannerServiceImpl service(AtomicReference<ChatCompletionCommand> captured, TokenUsage usage) {
        ChatCompletionGateway gateway = command -> {
            captured.set(command);
            return Mono.just(new ChatCompletionResult(
                    "{\"action\":\"EXECUTE_REACT\",\"capabilityCode\":\"support\"}", usage));
        };
        PromptRuntimeService promptService = promptVersionId ->
                new PromptRuntimeSnapshot(promptVersionId, "你负责规划任务。", List.of(), PromptRole.SYSTEM);
        TokenCountEstimator estimator = new DeterministicTokenEstimator();
        return new PlannerServiceImpl(gateway, modelService(), promptService, new PromptTemplateRenderer(), estimator,
                new ExecutionDecisionParser(), new ExecutionDecisionValidator());
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
                .schemaVersion(4)
                .defaultAgent(new ApplicationAgentReference(1L, 2L, "默认助手", "处理普通请求"))
                .capabilityCatalog(CapabilityCatalogSnapshot.builder().capabilities(capabilities).build())
                .plannerPolicy(planner)
                .build();
        return ConversationContext.builder()
                .applicationSnapshot(new ApplicationRuntimeSnapshot(1L, "planner-app", 2L, definition))
                .loginUser(user)
                .turn(turn)
                .originalQuery("处理售后问题")
                .contextualizedQuery("处理售后问题")
                .recentMessages(List.of())
                .budget(ContextBudget.builder().maxHistoryRounds(5).maxSystemPromptTokens(4000).maxCapabilityTokens(4000)
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
